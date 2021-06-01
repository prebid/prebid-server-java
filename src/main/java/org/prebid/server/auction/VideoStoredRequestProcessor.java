package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.IncludeBrandCategory;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.request.video.Podconfig;
import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.validation.VideoRequestValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executes stored request processing for video.
 */
public class VideoStoredRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VideoStoredRequestProcessor.class);

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_BUYERUID = "appnexus";

    private final boolean enforceStoredRequest;
    private final List<String> blacklistedAccounts;
    private final long defaultTimeout;
    private final String currency;
    private final BidRequest defaultBidRequest;
    private final ApplicationSettings applicationSettings;
    private final VideoRequestValidator validator;
    private final Metrics metrics;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;
    private final JsonMerger jsonMerger;

    private VideoStoredRequestProcessor(boolean enforceStoredRequest,
                                        List<String> blacklistedAccounts,
                                        long defaultTimeout,
                                        String adServerCurrency,
                                        BidRequest defaultBidRequest,
                                        ApplicationSettings applicationSettings,
                                        VideoRequestValidator validator,
                                        Metrics metrics,
                                        TimeoutFactory timeoutFactory,
                                        TimeoutResolver timeoutResolver,
                                        JacksonMapper mapper,
                                        JsonMerger jsonMerger) {

        this.enforceStoredRequest = enforceStoredRequest;
        this.blacklistedAccounts = blacklistedAccounts;
        this.defaultTimeout = defaultTimeout;
        this.currency = StringUtils.isBlank(adServerCurrency) ? DEFAULT_CURRENCY : adServerCurrency;
        this.defaultBidRequest = defaultBidRequest;
        this.applicationSettings = applicationSettings;
        this.validator = validator;
        this.metrics = metrics;
        this.timeoutFactory = timeoutFactory;
        this.timeoutResolver = timeoutResolver;
        this.mapper = mapper;
        this.jsonMerger = jsonMerger;
    }

    public static VideoStoredRequestProcessor create(boolean enforceStoredRequest,
                                                     List<String> blacklistedAccounts,
                                                     long defaultTimeout,
                                                     String adServerCurrency,
                                                     String defaultBidRequestPath,
                                                     FileSystem fileSystem,
                                                     ApplicationSettings applicationSettings,
                                                     VideoRequestValidator validator,
                                                     Metrics metrics,
                                                     TimeoutFactory timeoutFactory,
                                                     TimeoutResolver timeoutResolver,
                                                     JacksonMapper mapper,
                                                     JsonMerger jsonMerger) {

        return new VideoStoredRequestProcessor(
                enforceStoredRequest,
                Objects.requireNonNull(blacklistedAccounts),
                defaultTimeout,
                adServerCurrency,
                readBidRequest(
                        defaultBidRequestPath, Objects.requireNonNull(fileSystem), Objects.requireNonNull(mapper)),
                Objects.requireNonNull(applicationSettings),
                Objects.requireNonNull(validator),
                Objects.requireNonNull(metrics),
                Objects.requireNonNull(timeoutFactory),
                Objects.requireNonNull(timeoutResolver),
                Objects.requireNonNull(mapper),
                Objects.requireNonNull(jsonMerger));
    }

    /**
     * Fetches ParsedStoredDataResult&lt;BidRequestVideo, Imp&gt; from stored request.
     */
    public Future<WithPodErrors<BidRequest>> processVideoRequest(String accountId,
                                                                 String storedBidRequestId,
                                                                 Set<String> podIds,
                                                                 BidRequestVideo receivedRequest) {
        final Set<String> storedRequestIds = StringUtils.isNotBlank(storedBidRequestId)
                ? Collections.singleton(storedBidRequestId)
                : Collections.emptySet();

        return applicationSettings.getVideoStoredData(accountId, storedRequestIds, podIds,
                timeoutFactory.create(defaultTimeout))
                .compose(storedDataResult -> updateMetrics(storedDataResult, storedRequestIds, podIds))
                .map(storedData -> mergeToBidRequest(storedData, receivedRequest, storedBidRequestId))
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored request fetching failed: %s", exception.getMessage()))));
    }

    private static BidRequest readBidRequest(String defaultBidRequestPath, FileSystem fileSystem,
                                             JacksonMapper mapper) {

        return StringUtils.isNotBlank(defaultBidRequestPath)
                ? mapper.decodeValue(fileSystem.readFileBlocking(defaultBidRequestPath), BidRequest.class)
                : null;
    }

    private Future<StoredDataResult> updateMetrics(StoredDataResult storedDataResult, Set<String> requestIds,
                                                   Set<String> impIds) {
        requestIds.forEach(
                id -> metrics.updateStoredRequestMetric(storedDataResult.getStoredIdToRequest().containsKey(id)));
        impIds.forEach(id -> metrics.updateStoredImpsMetric(storedDataResult.getStoredIdToImp().containsKey(id)));

        return Future.succeededFuture(storedDataResult);
    }

    private WithPodErrors<BidRequest> mergeToBidRequest(StoredDataResult storedResult,
                                                        BidRequestVideo receivedRequest,
                                                        String storedBidRequestId) {
        final BidRequestVideo mergedStoredRequest = mergeBidRequest(receivedRequest, storedBidRequestId, storedResult);

        validator.validateStoredBidRequest(mergedStoredRequest, enforceStoredRequest, blacklistedAccounts);

        final Podconfig podconfig = mergedStoredRequest.getPodconfig();
        final Video video = mergedStoredRequest.getVideo();
        final Map<String, String> storedIdToImp = storedResult.getStoredIdToImp();
        final WithPodErrors<List<Imp>> impsToPodErrors = mergeStoredImps(podconfig, video, storedIdToImp);

        final BidRequest bidRequest = mergeWithDefaultBidRequest(mergedStoredRequest, impsToPodErrors.getData());

        return WithPodErrors.of(bidRequest, impsToPodErrors.getPodErrors());
    }

    private BidRequestVideo mergeBidRequest(BidRequestVideo originalRequest, String storedRequestId,
                                            StoredDataResult storedDataResult) {
        final String storedRequest = storedDataResult.getStoredIdToRequest().get(storedRequestId);
        if (enforceStoredRequest && StringUtils.isBlank(storedRequest)) {
            throw new InvalidRequestException("Stored request is enforced but not found");
        }

        return StringUtils.isNotBlank(storedRequest)
                ? jsonMerger.merge(originalRequest, storedRequest, storedRequestId, BidRequestVideo.class)
                : originalRequest;
    }

    private WithPodErrors<List<Imp>> mergeStoredImps(Podconfig podconfig, Video video,
                                                     Map<String, String> storedImpIdToJsonImp) {
        final Map<String, Imp> storedImpIdToImp = storedIdToStoredImp(storedImpIdToJsonImp);
        final WithPodErrors<List<Pod>> validPodsToPodErrors = validator.validPods(podconfig, storedImpIdToImp.keySet());
        final List<Pod> validPods = validPodsToPodErrors.getData();
        final List<PodError> podErrors = validPodsToPodErrors.getPodErrors();

        if (CollectionUtils.isEmpty(validPods)) {
            final String errorMessage = podErrors.stream()
                    .map(PodError::getPodErrors)
                    .map(errorsForPod -> String.join(", ", errorsForPod))
                    .collect(Collectors.joining("; "));
            throw new InvalidRequestException("all pods are incorrect:  " + errorMessage);
        }

        final List<Imp> imps = createImps(storedImpIdToImp, validPods, podconfig, video);
        return WithPodErrors.of(imps, podErrors);
    }

    private Map<String, Imp> storedIdToStoredImp(Map<String, String> storedIdToImp) {
        final Map<String, Imp> idToImps = new HashMap<>();
        if (MapUtils.isNotEmpty(storedIdToImp)) {
            for (Map.Entry<String, String> idToImp : storedIdToImp.entrySet()) {
                try {
                    idToImps.put(idToImp.getKey(), mapper.mapper().readValue(idToImp.getValue(), Imp.class));
                } catch (JsonProcessingException e) {
                    logger.error(e.getMessage());
                }
            }
        }
        return idToImps;
    }

    private static List<Imp> createImps(Map<String, Imp> idToImps, List<Pod> validPods, Podconfig podconfig,
                                        Video video) {
        final List<Integer> durationRangeSec = podconfig.getDurationRangeSec();
        final Boolean requireExactDuration = podconfig.getRequireExactDuration();
        final Tuple2<Integer, Integer> maxMin = maxMin(durationRangeSec);

        final ArrayList<Imp> imps = new ArrayList<>();
        for (Pod pod : validPods) {

            final Integer adpodDurationSec = pod.getAdpodDurationSec();
            int numImps = adpodDurationSec / maxMin.getLeft();

            if (BooleanUtils.isTrue(requireExactDuration) || numImps == 0) {
                // In case of impressions number is less than durations array,
                // we bump up impressions number up to duration array size
                // with this handler we will have one impression per specified duration
                numImps = Math.max(numImps, durationRangeSec.size());
            }
            int impDivNumber = numImps / durationRangeSec.size();

            for (int i = 0; i < numImps; i++) {
                Integer maxDuration;
                Integer minDuration = null;
                if (BooleanUtils.isTrue(requireExactDuration)) {
                    int durationIndex = (i + 1) / impDivNumber;
                    if (durationIndex > durationRangeSec.size() - 1) {
                        durationIndex = durationRangeSec.size() - 1;
                    }
                    maxDuration = durationRangeSec.get(durationIndex);
                    minDuration = durationRangeSec.get(durationIndex);
                } else {
                    maxDuration = maxMin.getRight();
                }

                final String podConfigId = pod.getConfigId();
                final Imp storedImp = idToImps.get(podConfigId);

                // Should never happen
                if (storedImp == null) {
                    continue;
                }
                final Imp imp = storedImp.toBuilder()
                        .id(String.format("%d_%d", pod.getPodId(), i))
                        .video(updateVideo(video, maxDuration, minDuration))
                        .build();
                imps.add(imp);
            }
        }
        return imps;
    }

    private static Tuple2<Integer, Integer> maxMin(List<Integer> values) {
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (Integer value : values) {
            max = Math.max(max, value);
            min = Math.min(min, value);
        }
        return Tuple2.of(max, min);
    }

    private static Video updateVideo(Video video, Integer maxDuration, Integer minDuration) {
        return video.toBuilder()
                .maxduration(maxDuration)
                .minduration(minDuration)
                .build();
    }

    private BidRequest mergeWithDefaultBidRequest(BidRequestVideo validatedVideoRequest, List<Imp> imps) {
        final BidRequest.BidRequestBuilder bidRequestBuilder =
                defaultBidRequest != null ? defaultBidRequest.toBuilder() : BidRequest.builder();

        final Site site = validatedVideoRequest.getSite();
        if (site != null) {
            final Site.SiteBuilder siteBuilder = site.toBuilder();
            final Content content = validatedVideoRequest.getContent();
            if (content != null) {
                siteBuilder.content(content);
            }
            siteBuilder.content(content);
            bidRequestBuilder.site(siteBuilder.build());
        }

        final App app = validatedVideoRequest.getApp();
        if (app != null) {
            final App.AppBuilder appBuilder = app.toBuilder();
            final Content content = validatedVideoRequest.getContent();
            if (content != null) {
                appBuilder.content(content);
            }
            bidRequestBuilder.app(appBuilder.build());
        }

        final Device device = validatedVideoRequest.getDevice();
        if (device != null) {
            bidRequestBuilder.device(device);
        }

        final User user = validatedVideoRequest.getUser();
        if (user != null) {
            final User updatedUser = user.toBuilder()
                    .buyeruid(DEFAULT_BUYERUID)
                    .build();
            bidRequestBuilder.user(updatedUser);
        }

        final List<String> bcat = validatedVideoRequest.getBcat();
        if (CollectionUtils.isNotEmpty(bcat)) {
            bidRequestBuilder.bcat(bcat);
        }

        final List<String> badv = validatedVideoRequest.getBadv();
        if (CollectionUtils.isNotEmpty(badv)) {
            bidRequestBuilder.badv(badv);
        }

        final Regs regs = validatedVideoRequest.getRegs();
        if (regs != null) {
            bidRequestBuilder.regs(regs);
        }

        final Long videoTmax = timeoutResolver.resolve(validatedVideoRequest.getTmax());
        bidRequestBuilder.tmax(videoTmax);

        addRequiredOpenRtbFields(bidRequestBuilder);

        return bidRequestBuilder
                .id("bid_id")
                .imp(imps)
                .ext(createBidExtension(validatedVideoRequest))
                .test(validatedVideoRequest.getTest())
                .build();
    }

    private void addRequiredOpenRtbFields(BidRequest.BidRequestBuilder bidRequestBuilder) {
        bidRequestBuilder.cur(Collections.singletonList(currency));
    }

    private ExtRequest createBidExtension(BidRequestVideo videoRequest) {
        final IncludeBrandCategory includebrandcategory = videoRequest.getIncludebrandcategory();
        final ExtIncludeBrandCategory extIncludeBrandCategory;
        if (includebrandcategory != null) {
            extIncludeBrandCategory = ExtIncludeBrandCategory.of(
                    includebrandcategory.getPrimaryAdserver(), includebrandcategory.getPublisher(), true);
        } else {
            extIncludeBrandCategory = ExtIncludeBrandCategory.of(null, null, false);
        }

        List<Integer> durationRangeSec = null;
        if (BooleanUtils.isFalse(videoRequest.getPodconfig().getRequireExactDuration())) {
            durationRangeSec = videoRequest.getPodconfig().getDurationRangeSec();
        }

        PriceGranularity updatedPriceGranularity = PriceGranularity.createFromString("med");
        final PriceGranularity priceGranularity = videoRequest.getPriceGranularity();
        if (priceGranularity != null) {
            final Integer precision = priceGranularity.getPrecision();
            if (precision != null && precision != 0) {
                updatedPriceGranularity = priceGranularity;
            }
        }

        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.mapper().valueToTree(updatedPriceGranularity))
                .includebidderkeys(true)
                .includebrandcategory(extIncludeBrandCategory)
                .durationrangesec(durationRangeSec)
                .build();

        final ExtRequestPrebidCache extReqPrebidCache = ExtRequestPrebidCache.of(null,
                ExtRequestPrebidCacheVastxml.of(null, null), null);

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .cache(extReqPrebidCache)
                .targeting(targeting)
                .build();

        return ExtRequest.of(extRequestPrebid);
    }
}

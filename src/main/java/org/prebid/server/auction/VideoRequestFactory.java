package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.IncludeBrandCategory;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.request.video.Podconfig;
import com.iab.openrtb.request.video.VideoUser;
import com.iab.openrtb.request.video.VideoVideo;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.settings.model.ParsedStoredDataResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class VideoRequestFactory {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_BUYERUID = "appnexus";
    private static final Long DEFAULT_TMAX = 5000L;
    private final int maxRequestSize;
    private final boolean enforceStoredRequest;
    private final List<String> blacklistedAccounts;
    private final StoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;
    private final TimeoutResolver timeoutResolver;
    private final BidRequest defaultBidRequest;
    private final String currency;

    public VideoRequestFactory(int maxRequestSize, boolean enforceStoredRequest, List<String> blacklistedAccounts,
                               StoredRequestProcessor storedRequestProcessor,
                               AuctionRequestFactory auctionRequestFactory, TimeoutResolver timeoutResolver,
                               BidRequest defaultBidRequest, String adServerCurrency) {
        this.maxRequestSize = maxRequestSize;
        this.enforceStoredRequest = enforceStoredRequest;
        this.blacklistedAccounts = blacklistedAccounts;
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.defaultBidRequest = Objects.requireNonNull(defaultBidRequest);
        this.currency = StringUtils.isBlank(adServerCurrency) ? DEFAULT_CURRENCY : adServerCurrency;
    }

    /**
     * Creates {@link AuctionContext} and {@link List} of {@link PodError} based on {@link RoutingContext}.
     */
    public Future<Tuple2<AuctionContext, List<PodError>>> fromRequest(RoutingContext routingContext, long startTime) {

        final BidRequestVideo incomingBidRequest;
        try {
            incomingBidRequest = parseRequest(routingContext);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final String storedRequestId = incomingBidRequest.getStoredrequestid();
        if (StringUtils.isBlank(storedRequestId) && enforceStoredRequest) {
            return Future.failedFuture(new InvalidRequestException("Unable to find required stored request id"));
        }

        final Set<String> podConfigIds = podConfigIds(incomingBidRequest);
        return createBidRequest(routingContext, incomingBidRequest, storedRequestId, podConfigIds)
                .compose(bidRequestToPodError -> auctionRequestFactory
                        .toAuctionContext(routingContext, bidRequestToPodError.getLeft(), startTime, timeoutResolver)
                        .map(auctionContext -> Tuple2.of(auctionContext, bidRequestToPodError.getRight())));
    }

    /**
     * Parses request body to {@link BidRequestVideo}.
     * <p>
     * Throws {@link InvalidRequestException} if body is empty, exceeds max request size or couldn't be deserialized.
     */
    private BidRequestVideo parseRequest(RoutingContext context) {
        final Buffer body = context.getBody();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        }

        try {
            return Json.decodeValue(body, BidRequestVideo.class);
        } catch (DecodeException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private static Set<String> podConfigIds(BidRequestVideo incomingBidRequest) {
        final Podconfig podconfig = incomingBidRequest.getPodconfig();
        if (podconfig != null) {
            return podconfig.getPods().stream()
                    .map(Pod::getConfigId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    private Future<Tuple2<BidRequest, List<PodError>>> createBidRequest(RoutingContext routingContext,
                                                                        BidRequestVideo bidRequestVideo,
                                                                        String storedVideoId,
                                                                        Set<String> podConfigIds) {
        return storedRequestProcessor.processVideoRequest(storedVideoId, podConfigIds, bidRequestVideo)
                .map(storedData -> doAndReturn(() -> validateStoredBidRequest(storedData.getStoredData()), storedData))
                .map(this::mergeWithDefaultBidRequest)
                .map(bidRequestToErrors -> Tuple2.of(auctionRequestFactory
                                .fillImplicitParameters(bidRequestToErrors.getLeft(), routingContext, timeoutResolver),
                        bidRequestToErrors.getRight()))
                .map(requestToPodErrors ->
                        Tuple2.of(auctionRequestFactory.validateRequest(requestToPodErrors.getLeft()),
                                requestToPodErrors.getRight()));
    }

    private static <T> T doAndReturn(Runnable runnable, T returned) {
        runnable.run();
        return returned;
    }

    /**
     * Throws {@link InvalidRequestException} in case of invalid {@link BidRequestVideo}.
     */
    private void validateStoredBidRequest(BidRequestVideo bidRequestVideo) {
        if (enforceStoredRequest && StringUtils.isBlank(bidRequestVideo.getStoredrequestid())) {
            throw new InvalidRequestException("request missing required field: storedrequestid");
        }
        final Podconfig podconfig = bidRequestVideo.getPodconfig();
        if (podconfig == null) {
            throw new InvalidRequestException("request missing required field: PodConfig");
        } else {
            final List<Integer> durationRangeSec = podconfig.getDurationRangeSec();
            if (CollectionUtils.isEmpty(durationRangeSec) || isZeroOrNegativeDuration(durationRangeSec)) {
                throw new InvalidRequestException("duration array require only positive numbers");
            }
            final List<Pod> pods = podconfig.getPods();
            if (CollectionUtils.sizeIsEmpty(pods)) {
                throw new InvalidRequestException("request missing required field: PodConfig.Pods");
            }
        }

        validateSiteAndApp(bidRequestVideo.getSite(), bidRequestVideo.getApp());
        validateVideo(bidRequestVideo.getVideo());
    }

    private void validateSiteAndApp(Site site, App app) {
        if (app == null && site == null) {
            throw new InvalidRequestException("request missing required field: site or app");
        } else if (app != null && site != null) {
            throw new InvalidRequestException("request.site or request.app must be defined, but not both");
        } else if (site != null && StringUtils.isBlank(site.getId()) && StringUtils.isBlank(site.getPage())) {
            throw new InvalidRequestException("request.site missing required field: id or page");
        } else if (app != null) {
            final String appId = app.getId();

            if (StringUtils.isNotBlank(appId)) {
                if (blacklistedAccounts.contains(appId)) {
                    throw new InvalidRequestException("Prebid-server does not process requests from App ID: "
                            + appId);
                }
            } else {
                if (StringUtils.isBlank(app.getBundle())) {
                    throw new InvalidRequestException("request.app missing required field: id or bundle");
                }
            }
        }
    }

    private static void validateVideo(VideoVideo video) {
        if (video == null) {
            throw new InvalidRequestException("request missing required field: Video");
        }
        final List<String> mimes = video.getMimes();
        if (CollectionUtils.isEmpty(mimes)) {
            throw new InvalidRequestException("request missing required field: Video.Mimes");
        } else {
            final List<String> notBlankMimes = mimes.stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(notBlankMimes)) {
                throw new InvalidRequestException(
                        "request missing required field: Video.Mimes, mime types contains empty strings only");
            }
        }

        if (CollectionUtils.isEmpty(video.getProtocols())) {
            throw new InvalidRequestException("request missing required field: Video.Protocols");
        }
    }

    private static boolean isZeroOrNegativeDuration(List<Integer> durationRangeSec) {
        return durationRangeSec.stream()
                .anyMatch(duration -> duration <= 0);
    }

    private static Tuple2<List<Pod>, List<PodError>> validPods(BidRequestVideo bidRequestVideo,
                                                               Set<String> storedPodConfigIds) {
        final List<Pod> pods = bidRequestVideo.getPodconfig().getPods();
        final List<PodError> podErrors = validateEachPod(pods);
        final List<Integer> errorPodIds = podErrors.stream()
                .map(PodError::getPodId)
                .collect(Collectors.toList());

        final List<Pod> validPods = new ArrayList<>();
        for (int i = 0; i < pods.size(); i++) {
            final Pod pod = pods.get(i);
            final Integer podId = pod.getPodId();
            final String configId = pod.getConfigId();
            if (!errorPodIds.contains(podId)) {
                if (storedPodConfigIds.contains(configId)) {
                    validPods.add(pod);
                } else {
                    podErrors.add(PodError.of(podId, i, Collections.singletonList("unable to load Pod id: " + podId)));
                }
            }
        }

        return Tuple2.of(validPods, podErrors);
    }

    // Should be called only after validation
    private static List<PodError> validateEachPod(List<Pod> pods) {
        final List<PodError> podErrorsResult = new ArrayList<>();

        final Map<Integer, Boolean> podIdToFlag = new HashMap<>();
        for (int i = 0; i < pods.size(); i++) {
            final List<String> podErrors = new ArrayList<>();
            final Pod pod = pods.get(i);

            final Integer podId = pod.getPodId();
            if (BooleanUtils.isTrue(podIdToFlag.get(podId))) {
                podErrors.add("request duplicated required field: PodConfig.Pods.PodId, Pod id: " + podId);
            } else {
                podIdToFlag.put(podId, true);
            }
            if (podId == null || podId <= 0) {
                podErrors.add("request missing required field: PodConfig.Pods.PodId, Pod index: " + i);
            }
            final Integer adpodDurationSec = pod.getAdpodDurationSec();
            if (adpodDurationSec != null) {
                if (adpodDurationSec == 0) {
                    podErrors.add(
                            "request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec, Pod index: "
                                    + i);
                }
                if (adpodDurationSec < 0) {
                    podErrors.add(
                            "request incorrect required field: PodConfig.Pods.AdPodDurationSec is negative, Pod index: "
                                    + i);
                }
            } else {
                podErrors.add(
                        "request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec, Pod index: "
                                + i);
            }
            if (StringUtils.isBlank(pod.getConfigId())) {
                podErrors.add("request missing or incorrect required field: PodConfig.Pods.ConfigId, Pod index: " + i);
            }
            if (!podErrors.isEmpty()) {
                podErrorsResult.add(PodError.of(podId, i, podErrors));
            }
        }
        return podErrorsResult;
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

    // Should be called only after validation
    private Tuple2<BidRequest, List<PodError>> mergeWithDefaultBidRequest(
            ParsedStoredDataResult<BidRequestVideo, Imp> storedData) {

        // We should create imps first. We avoid too much Tuples for PodError
        final Tuple2<List<Imp>, List<PodError>> impsToErrors = mergeImpsForResponse(storedData);
        final BidRequest.BidRequestBuilder bidRequestBuilder = defaultBidRequest.toBuilder();

        final BidRequestVideo videoRequest = storedData.getStoredData();
        final Site site = videoRequest.getSite();
        if (site != null) {
            final Site.SiteBuilder siteBuilder = site.toBuilder();
            final Content content = videoRequest.getContent();
            if (content != null) {
                siteBuilder.content(content);
            }
            siteBuilder.content(content);
            bidRequestBuilder.site(siteBuilder.build());
        }

        final App app = videoRequest.getApp();
        if (app != null) {
            final App.AppBuilder appBuilder = app.toBuilder();
            final Content content = videoRequest.getContent();
            if (content != null) {
                appBuilder.content(content);
            }
            bidRequestBuilder.app(appBuilder.build());
        }

        final Device device = videoRequest.getDevice();
        if (device != null) {
            bidRequestBuilder.device(device);
        }

        final VideoUser user = videoRequest.getUser();
        if (user != null) {
            final User updatedUser = User.builder()
                    .buyeruid(DEFAULT_BUYERUID)
                    .yob(user.getYob())
                    .gender(user.getGender())
                    .keywords(user.getKeywords())
                    .build();
            bidRequestBuilder.user(updatedUser);
        }

        final List<String> bcat = videoRequest.getBcat();
        if (CollectionUtils.isNotEmpty(bcat)) {
            bidRequestBuilder.bcat(bcat);
        }

        final List<String> badv = videoRequest.getBadv();
        if (CollectionUtils.isNotEmpty(badv)) {
            bidRequestBuilder.badv(badv);
        }

        final Long videoTmax = videoRequest.getTmax();
        if (videoTmax == null || videoTmax == 0) {
            bidRequestBuilder.tmax(DEFAULT_TMAX);
        } else {
            bidRequestBuilder.tmax(videoRequest.getTmax());
        }

        addRequiredOpenRtbFields(bidRequestBuilder);

        final List<Imp> imps = impsToErrors.getLeft();
        final BidRequest bidRequest = bidRequestBuilder
                .id("bid_id")
                .imp(imps)
                .ext(createBidExtension(videoRequest))
                .test(videoRequest.getTest())
                .build();
        return Tuple2.of(bidRequest, impsToErrors.getRight());
    }

    private void addRequiredOpenRtbFields(BidRequest.BidRequestBuilder bidRequestBuilder) {
        bidRequestBuilder.cur(Collections.singletonList(currency));
    }

    private static ObjectNode createBidExtension(BidRequestVideo videoRequest) {
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
                .pricegranularity(Json.mapper.valueToTree(updatedPriceGranularity))
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

        return Json.mapper.valueToTree(ExtBidRequest.of(extRequestPrebid));
    }

    private static Tuple2<List<Imp>, List<PodError>> mergeImpsForResponse(
            ParsedStoredDataResult<BidRequestVideo, Imp> storedData) {

        final BidRequestVideo videoRequest = storedData.getStoredData();
        final Map<String, Imp> idToImps = storedData.getIdToImps();
        final Tuple2<List<Pod>, List<PodError>> validPodsToPodErrors = validPods(videoRequest, idToImps.keySet());
        final List<Pod> validPods = validPodsToPodErrors.getLeft();
        final List<PodError> podErrors = validPodsToPodErrors.getRight();

        if (CollectionUtils.isEmpty(validPods)) {
            final String errorMessage = podErrors.stream()
                    .map(PodError::getPodErrors)
                    .map(errorsForPod -> String.join(", ", errorsForPod))
                    .collect(Collectors.joining("; "));
            throw new InvalidRequestException("all pods are incorrect:  " + errorMessage);
        }

        final Podconfig podconfig = videoRequest.getPodconfig();
        final VideoVideo video = videoRequest.getVideo();
        final List<Imp> imps = createImps(idToImps, validPods, podconfig, video);
        return Tuple2.of(imps, podErrors);
    }

    private static List<Imp> createImps(Map<String, Imp> idToImps, List<Pod> validPods, Podconfig podconfig,
                                        VideoVideo video) {
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
                        .video(createVideo(video, maxDuration, minDuration))
                        .build();
                imps.add(imp);
            }
        }
        return imps;
    }

    private static Video createVideo(VideoVideo video, Integer maxDuration, Integer minDuration) {
        return Video.builder()
                .w(video.getW())
                .h(video.getH())
                .protocols(video.getProtocols())
                .mimes(video.getMimes())
                .maxduration(maxDuration)
                .minduration(minDuration)
                .build();
    }
}

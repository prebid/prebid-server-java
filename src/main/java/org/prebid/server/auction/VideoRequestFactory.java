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
import com.iab.openrtb.request.video.Podconfig;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
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

    private static final String STORED_ID_REQUEST_PARAM = "storedrequestid";
    private static final Long DEFAULT_TMAX = 5000L;
    private final List<String> blacklistedAccounts;
    private final StoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;
    private final TimeoutResolver timeoutResolver;
    private boolean videoStoredRequestRequired;

    public VideoRequestFactory(List<String> blacklistedAccounts, StoredRequestProcessor storedRequestProcessor,
                               AuctionRequestFactory auctionRequestFactory, TimeoutResolver timeoutResolver,
                               boolean videoStoredRequestRequired) {
        this.blacklistedAccounts = blacklistedAccounts;

        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.videoStoredRequestRequired = videoStoredRequestRequired;
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final String tagId = routingContext.request().getParam(STORED_ID_REQUEST_PARAM);
        if (StringUtils.isBlank(tagId)) {
            return Future.failedFuture(new InvalidRequestException("Unable to find required stored request id"));
        }

        final BidRequestVideo incomingBidRequest;
        try {
            incomingBidRequest = parseRequest(routingContext);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }
        final Set<String> podIds = podIds(incomingBidRequest);

        return createBidRequest(routingContext, incomingBidRequest, tagId, podIds)
                .compose(bidRequest ->
                        auctionRequestFactory.toAuctionContext(routingContext, bidRequest, startTime, timeoutResolver));
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

    private static Set<String> podIds(BidRequestVideo incomingBidRequest) {
        final Podconfig podconfig = incomingBidRequest.getPodconfig();
        if (podconfig != null) {
            return podconfig.getPods().stream()
                    .map(Pod::getPodId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }

    }

    /**
     * Creates {@link BidRequest} and sets properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> createBidRequest(RoutingContext routingContext, BidRequestVideo bidRequestVideo,
                                                String storedVideoId, Set<String> podIds) {
        return storedRequestProcessor.processVideoRequest(storedVideoId, podIds, bidRequestVideo)
                .map(storedData -> doAndReturn(() -> validateStoredBidRequest(storedData.getStoredData()), storedData))
                .map(storedData -> mergeWithDefaultBidRequest(storedData.getStoredData(), createImps(storedData)))
                .map(bidRequest -> auctionRequestFactory.fillImplicitParameters(bidRequest, routingContext, timeoutResolver))
                .map(auctionRequestFactory::validateRequest)
                .recover();
    }

    private static List<Imp> createImps(ParsedStoredDataResult<BidRequestVideo, Imp> storedData) {
        final BidRequestVideo videoRequest = storedData.getStoredData();
        final Map<String, Imp> idToImps = storedData.getIdToimps();
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
        final List<Integer> durationRangeSec = podconfig.getDurationRangeSec();
        final Tuple2<Integer, Integer> maxMin = maxMin(durationRangeSec);
        final Boolean requireExactDuration = podconfig.getRequireExactDuration();
        final Video video = videoRequest.getVideo();

        final List<Imp> imps = new ArrayList<>();
        for (Pod pod : validPods) {

            final Integer adpodDurationSec = pod.getAdpodDurationSec();
            int numImps = adpodDurationSec / maxMin.getLeft();

            if (requireExactDuration) {
                // In case of impressions number is less than durations array, we bump up impressions number up to duration array size
                // with this handler we will have one impression per specified duration
                numImps = Math.max(numImps, durationRangeSec.size());
            }
            int impDivNumber = numImps / durationRangeSec.size();

            for (int i = 0; i < numImps; i++) {
                Integer maxDuration;
                Integer minDuration = null;
                if (requireExactDuration) {
                    int durationIndex = (i + 1) / impDivNumber;
                    if (durationIndex > durationRangeSec.size() - 1) {
                        durationIndex = durationRangeSec.size() - 1;
                    }
                    maxDuration = durationRangeSec.get(durationIndex);
                    minDuration = durationRangeSec.get(durationIndex);
                } else {
                    maxDuration = maxMin.getRight();
                }

                final Integer podId = pod.getPodId();
                final Imp storedImp = idToImps.get(String.valueOf(podId));

                // Should never happen
                if (storedImp == null) {
                    continue;
                }
                final Imp imp = storedImp.toBuilder()
                        .id(String.format("%d_%d", podId, i))
                        .video(createVideo(video, maxDuration, minDuration))
                        .build();
                imps.add(imp);
            }
        }
        return Tuple2.of(imps, podErrors);
    }

    private static Video createVideo(Video video, Integer maxDuration, Integer minDuration) {
        return Video.builder()
                .w(video.getW())
                .h(video.getH())
                .protocols(video.getProtocols())
                .mimes(video.getMimes())
                .maxduration(maxDuration)
                .minduration(minDuration)
                .build();
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

    private static Tuple2<List<Pod>, List<PodError>> validPods(BidRequestVideo bidRequestVideo,
                                                               Set<String> validPodIds) {
        final List<Pod> pods = bidRequestVideo.getPodconfig().getPods();
        final List<PodError> podErrors = validateEachPod(pods);
        if (CollectionUtils.isNotEmpty(podErrors)) {
            final List<Integer> errorPodIds = podErrors.stream()
                    .map(PodError::getPodId)
                    .collect(Collectors.toList());

            final List<Pod> validPods = new ArrayList<>();
            for (int i = 0; i < pods.size(); i++) {
                final Pod pod = pods.get(i);
                final Integer podId = pod.getPodId();
                if (!errorPodIds.contains(podId)) {
                    if (validPodIds.contains(String.valueOf(podId))) {
                        validPods.add(pod);
                    } else {
                        podErrors.add(PodError.of(podId, i, Collections.singletonList("unable to load Pod id: " + podId)));
                    }
                }
            }

            return Tuple2.of(validPods, podErrors);
        }
        return Tuple2.of(pods, Collections.emptyList());
    }

    //Should be called only after validation
    private static BidRequest mergeWithDefaultBidRequest(BidRequestVideo videoRequest, List<Imp> imps) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = defaultBidRequest().toBuilder();

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

        final User user = videoRequest.getUser();
        if (user != null) {
            final User updatedUser = User.builder()
                    .buyeruid("appnexus")
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

        return bidRequestBuilder
                .id("bid_id")
                .imp(imps)
                .ext(createBidExtension(videoRequest))
                .test(videoRequest.getTest())
                .build();
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

        PriceGranularity priceGranularity = PriceGranularity.createFromString("med");
        final Integer precision = videoRequest.getPriceGranularity().getPrecision();
        if (precision != null && precision != 0) {
            priceGranularity = videoRequest.getPriceGranularity();
        }

        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(priceGranularity), null, null, true, extIncludeBrandCategory, durationRangeSec, null);

        final ExtRequestPrebidCache extReqPrebidCache = ExtRequestPrebidCache.of(null, ExtRequestPrebidCacheVastxml.of(null, null), null);

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .cache(extReqPrebidCache)
                .targeting(targeting)
                .build();

        return Json.mapper.valueToTree(ExtBidRequest.of(extRequestPrebid));
    }

    //TODO
    private static BidRequest defaultBidRequest() {
        return BidRequest.builder().build();
    }

    private static <T> T doAndReturn(Runnable runnable, T returned) {
        runnable.run();
        return returned;
    }

    /**
     * Throws {@link InvalidRequestException} in case of invalid {@link BidRequestVideo}.
     */
    private void validateStoredBidRequest(BidRequestVideo bidRequestVideo) {
        if (videoStoredRequestRequired && StringUtils.isBlank(bidRequestVideo.getStoredrequestid())) {
            throw new InvalidRequestException("request missing required field: storedrequestid");
        }
        final Podconfig podconfig = bidRequestVideo.getPodconfig();
        if (podconfig == null) {
            throw new InvalidRequestException("request missing required field: PodConfig");
        } else {
            final List<Pod> pods = podconfig.getPods();
            if (CollectionUtils.isEmpty(pods)) {
                throw new InvalidRequestException("request missing required field: PodConfig.DurationRangeSec");
            }
            if (isZeroOrNegativeDuration(podconfig.getDurationRangeSec())) {
                throw new InvalidRequestException("duration array cannot contain negative or zero values");
            }
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
                    throw new InvalidRequestException("Prebid-server does not process requests from App ID: %s" + appId);
                }
            } else {
                if (StringUtils.isBlank(app.getBundle())) {
                    throw new InvalidRequestException("request.app missing required field: id or bundle");
                }
            }
        }
    }

    private static void validateVideo(Video video) {
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
            if (CollectionUtils.isNotEmpty(notBlankMimes)) {
                throw new InvalidRequestException("request missing required field: Video.Mimes, mime types contains empty strings only");
            }
        }

        if (CollectionUtils.isEmpty(video.getProtocols())) {
            throw new InvalidRequestException("request missing required field: Video.Protocols");
        }
    }

    // Need to be called after Validation
    private static List<PodError> validateEachPod(List<Pod> pods) {
        final List<PodError> podErrorsResult = new ArrayList<>();

        final Map<Integer, Boolean> podIdToFlag = new HashMap<>();
        for (int i = 0; i < pods.size(); i++) {
            final List<String> podErrors = new ArrayList<>();
            final Pod pod = pods.get(i);

            final Integer podId = pod.getPodId();
            if (podIdToFlag.get(podId)) {
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

    private static boolean isZeroOrNegativeDuration(List<Integer> durationRangeSec) {
        if (durationRangeSec == null) {
            return false;
        }

        return durationRangeSec.stream()
                .anyMatch(duration -> duration <= 0);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class PodError {
        Integer podId;
        Integer podIndex;
        List<String> podErrors;
    }
}

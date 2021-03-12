package org.prebid.server.auction;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.request.video.Podconfig;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VideoRequestFactory {

    private static final String DEBUG = "debug";
    private static final int DEFAULT_CACHE_LOG_TTL = 3600;

    private final int maxRequestSize;
    private final boolean enforceStoredRequest;
    private final Pattern escapeLogCacheRegexPattern;
    private final VideoStoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;
    private final TimeoutResolver timeoutResolver;
    private final JacksonMapper mapper;

    public VideoRequestFactory(int maxRequestSize,
                               boolean enforceStoredRequest, String escapeLogCacheRegex,
                               VideoStoredRequestProcessor storedRequestProcessor,
                               AuctionRequestFactory auctionRequestFactory,
                               TimeoutResolver timeoutResolver,
                               JacksonMapper mapper) {

        this.enforceStoredRequest = enforceStoredRequest;
        this.maxRequestSize = maxRequestSize;
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.mapper = Objects.requireNonNull(mapper);

        this.escapeLogCacheRegexPattern = StringUtils.isNotBlank(escapeLogCacheRegex)
                ? Pattern.compile(escapeLogCacheRegex)
                : null;
    }

    /**
     * Creates {@link AuctionContext} and {@link List} of {@link PodError} based on {@link RoutingContext}.
     */
    public Future<WithPodErrors<AuctionContext>> fromRequest(RoutingContext routingContext, long startTime) {
        final boolean debugEnabled = BooleanUtils.toBoolean(routingContext.queryParams().get(DEBUG));
        final CachedDebugLog cachedDebugLog = new CachedDebugLog(debugEnabled, DEFAULT_CACHE_LOG_TTL,
                escapeLogCacheRegexPattern, mapper);
        final BidRequestVideo incomingBidRequest;
        try {
            incomingBidRequest = parseRequest(routingContext, cachedDebugLog);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final String storedRequestId = incomingBidRequest.getStoredrequestid();
        if (StringUtils.isBlank(storedRequestId) && enforceStoredRequest) {
            return Future.failedFuture(new InvalidRequestException("Unable to find required stored request id"));
        }

        final Set<String> podConfigIds = podConfigIds(incomingBidRequest);
        return createBidRequest(routingContext, incomingBidRequest, storedRequestId, podConfigIds, debugEnabled)
                .compose(bidRequestToPodError -> auctionRequestFactory.toAuctionContext(
                        routingContext,
                        bidRequestToPodError.getData(),
                        MetricName.video,
                        new ArrayList<>(),
                        startTime,
                        timeoutResolver)
                        .map(auctionContext -> updateContextWithDebugLog(auctionContext, cachedDebugLog))
                        .map(auctionContext -> WithPodErrors.of(auctionContext, bidRequestToPodError.getPodErrors())));
    }

    /**
     * Extracts publisher id either from {@link BidRequestVideo}.app.publisher
     * or {@link BidRequestVideo}.site.publisher. If neither is present returns empty string.
     */
    private String accountIdFrom(BidRequestVideo bidRequestVideo) {
        final App app = bidRequestVideo.getApp();
        final Publisher appPublisher = app != null ? app.getPublisher() : null;
        final Site site = bidRequestVideo.getSite();
        final Publisher sitePublisher = site != null ? site.getPublisher() : null;

        final Publisher publisher = ObjectUtils.defaultIfNull(appPublisher, sitePublisher);
        final String publisherId = publisher != null ? resolvePublisherId(publisher) : null;
        return ObjectUtils.defaultIfNull(publisherId, StringUtils.EMPTY);
    }

    /**
     * Resolves what value should be used as a publisher id - either taken from publisher.ext.parentAccount
     * or publisher.id in this respective priority.
     */
    private String resolvePublisherId(Publisher publisher) {
        final String parentAccountId = parentAccountIdFromExtPublisher(publisher.getExt());
        return ObjectUtils.defaultIfNull(parentAccountId, publisher.getId());
    }

    /**
     * Parses publisher.ext and returns parentAccount value from it. Returns null if any parsing error occurs.
     */
    private String parentAccountIdFromExtPublisher(ExtPublisher extPublisher) {
        final ExtPublisherPrebid extPublisherPrebid = extPublisher != null ? extPublisher.getPrebid() : null;
        return extPublisherPrebid != null ? StringUtils.stripToNull(extPublisherPrebid.getParentAccount()) : null;
    }

    private AuctionContext updateContextWithDebugLog(AuctionContext auctionContext,
                                                     CachedDebugLog cachedDebugLog) {
        return auctionContext.toBuilder().cachedDebugLog(cachedDebugLog).build();
    }

    /**
     * Parses request body to {@link BidRequestVideo}.
     * <p>
     * Throws {@link InvalidRequestException} if body is empty, exceeds max request size or couldn't be deserialized.
     */
    private BidRequestVideo parseRequest(RoutingContext context, CachedDebugLog cachedDebugLog) {
        final Buffer body = context.getBody();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        }

        if (cachedDebugLog.isEnabled()) {
            cachedDebugLog.setRequest(body.toString());
            cachedDebugLog.setHeadersLog(context.request().headers());
        }

        try {
            final BidRequestVideo bidRequestVideo = mapper.decodeValue(body, BidRequestVideo.class);
            return insertDeviceUa(context, bidRequestVideo);
        } catch (DecodeException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private BidRequestVideo insertDeviceUa(RoutingContext context, BidRequestVideo bidRequestVideo) {
        final Device device = bidRequestVideo.getDevice();
        final String deviceUa = device != null ? device.getUa() : null;
        if (StringUtils.isBlank(deviceUa)) {
            final String userAgentHeader = context.request().getHeader(HttpUtil.USER_AGENT_HEADER);
            if (StringUtils.isEmpty(userAgentHeader)) {
                throw new InvalidRequestException("Device.UA and User-Agent Header is not presented");
            }
            final Device.DeviceBuilder deviceBuilder = device == null ? Device.builder() : device.toBuilder();

            return bidRequestVideo.toBuilder()
                    .device(deviceBuilder
                            .ua(userAgentHeader)
                            .build())
                    .build();
        }
        return bidRequestVideo;
    }

    private static Set<String> podConfigIds(BidRequestVideo incomingBidRequest) {
        final Podconfig podconfig = incomingBidRequest.getPodconfig();
        if (podconfig != null && CollectionUtils.isNotEmpty(podconfig.getPods())) {
            return podconfig.getPods().stream()
                    .map(Pod::getConfigId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    private Future<WithPodErrors<BidRequest>> createBidRequest(RoutingContext routingContext,
                                                               BidRequestVideo bidRequestVideo,
                                                               String storedVideoId,
                                                               Set<String> podConfigIds,
                                                               boolean debugEnabled) {
        return storedRequestProcessor.processVideoRequest(accountIdFrom(bidRequestVideo), storedVideoId, podConfigIds,
                bidRequestVideo)
                .map(bidRequestToErrors -> fillImplicitParameters(routingContext, bidRequestToErrors, debugEnabled))
                .map(this::validateRequest);
    }

    private WithPodErrors<BidRequest> validateRequest(WithPodErrors<BidRequest> requestToPodErrors) {
        final BidRequest bidRequest = auctionRequestFactory.validateRequest(requestToPodErrors.getData());
        return WithPodErrors.of(bidRequest, requestToPodErrors.getPodErrors());
    }

    private WithPodErrors<BidRequest> fillImplicitParameters(RoutingContext routingContext,
                                                             WithPodErrors<BidRequest> bidRequestToErrors,
                                                             boolean debugEnabled) {
        BidRequest bidRequest = auctionRequestFactory
                .fillImplicitParameters(bidRequestToErrors.getData(), routingContext, timeoutResolver);
        if (debugEnabled) {
            bidRequest = bidRequest.toBuilder().test(1).build();
        }
        return WithPodErrors.of(bidRequest, bidRequestToErrors.getPodErrors());
    }
}

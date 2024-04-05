package org.prebid.server.auction.requestfactory;

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
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.VideoStoredRequestProcessor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.auction.privacy.contextfactory.AuctionPrivacyContextFactory;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

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
    private static final String ENDPOINT = Endpoint.openrtb2_video.value();

    private final int maxRequestSize;
    private final boolean enforceStoredRequest;
    private final Pattern escapeLogCacheRegexPattern;

    private final Ortb2RequestFactory ortb2RequestFactory;
    private final VideoStoredRequestProcessor storedRequestProcessor;
    private final BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    private final Ortb2ImplicitParametersResolver paramsResolver;
    private final AuctionPrivacyContextFactory auctionPrivacyContextFactory;
    private final DebugResolver debugResolver;
    private final JacksonMapper mapper;
    private final GeoLocationServiceWrapper geoLocationServiceWrapper;

    public VideoRequestFactory(int maxRequestSize,
                               boolean enforceStoredRequest,
                               String escapeLogCacheRegex,
                               Ortb2RequestFactory ortb2RequestFactory,
                               VideoStoredRequestProcessor storedRequestProcessor,
                               BidRequestOrtbVersionConversionManager ortbVersionConversionManager,
                               Ortb2ImplicitParametersResolver paramsResolver,
                               AuctionPrivacyContextFactory auctionPrivacyContextFactory,
                               DebugResolver debugResolver,
                               JacksonMapper mapper,
                               GeoLocationServiceWrapper geoLocationServiceWrapper) {

        this.enforceStoredRequest = enforceStoredRequest;
        this.maxRequestSize = maxRequestSize;
        this.ortb2RequestFactory = Objects.requireNonNull(ortb2RequestFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.ortbVersionConversionManager = Objects.requireNonNull(ortbVersionConversionManager);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
        this.auctionPrivacyContextFactory = Objects.requireNonNull(auctionPrivacyContextFactory);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.mapper = Objects.requireNonNull(mapper);
        this.geoLocationServiceWrapper = Objects.requireNonNull(geoLocationServiceWrapper);

        this.escapeLogCacheRegexPattern = StringUtils.isNotBlank(escapeLogCacheRegex)
                ? Pattern.compile(escapeLogCacheRegex)
                : null;
    }

    /**
     * Creates {@link AuctionContext} and {@link List} of {@link PodError} based on {@link RoutingContext}.
     */
    public Future<WithPodErrors<AuctionContext>> fromRequest(RoutingContext routingContext, long startTime) {
        final String body;
        try {
            body = extractAndValidateBody(routingContext);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final List<PodError> podErrors = new ArrayList<>();

        final AuctionContext initialAuctionContext = ortb2RequestFactory.createAuctionContext(
                Endpoint.openrtb2_video, MetricName.video);

        return ortb2RequestFactory.executeEntrypointHooks(routingContext, body, initialAuctionContext)
                .compose(httpRequest ->
                        createBidRequest(httpRequest)

                                .compose(bidRequest -> validateRequest(
                                        bidRequest,
                                        httpRequest,
                                        initialAuctionContext.getDebugWarnings()))

                                .map(bidRequestWithErrors -> populatePodErrors(
                                        bidRequestWithErrors.getPodErrors(), podErrors, bidRequestWithErrors))

                                .map(bidRequestWithErrors -> ortb2RequestFactory.enrichAuctionContext(
                                        initialAuctionContext, httpRequest, bidRequestWithErrors.getData(), startTime)))

                .compose(auctionContext -> ortb2RequestFactory.fetchAccountWithoutStoredRequestLookup(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(debugResolver.debugContextFrom(auctionContext)))

                .compose(auctionContext -> geoLocationServiceWrapper.lookup(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithGeolocationData(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.activityInfrastructureFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> auctionPrivacyContextFactory.contextFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.executeProcessedAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .map(ortb2RequestFactory::enrichWithPriceFloors)

                .map(ortb2RequestFactory::updateTimeout)

                .recover(ortb2RequestFactory::restoreResultFromRejection)

                .map(this::updateContextWithDebugLog)

                .map(auctionContext -> WithPodErrors.of(auctionContext, podErrors));
    }

    private String extractAndValidateBody(RoutingContext routingContext) {
        final String body = routingContext.getBodyAsString();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException("Request size exceeded max size of %d bytes.".formatted(maxRequestSize));
        }

        return body;
    }

    private Future<WithPodErrors<BidRequest>> createBidRequest(HttpRequestContext httpRequest) {
        final boolean debugEnabled = isDebugEnabled(httpRequest);

        final BidRequestVideo bidRequestVideo;
        try {
            bidRequestVideo = parseRequest(httpRequest);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final String storedRequestId = bidRequestVideo.getStoredrequestid();
        if (StringUtils.isBlank(storedRequestId) && enforceStoredRequest) {
            return Future.failedFuture(new InvalidRequestException("Unable to find required stored request id"));
        }

        final Set<String> podConfigIds = podConfigIds(bidRequestVideo);
        final String accountId = accountIdFrom(bidRequestVideo);

        return storedRequestProcessor.processVideoRequest(accountId, storedRequestId, podConfigIds, bidRequestVideo)

                .map(bidRequestToErrors -> WithPodErrors.of(
                        ortbVersionConversionManager.convertToAuctionSupportedVersion(bidRequestToErrors.getData()),
                        bidRequestToErrors.getPodErrors()))

                .map(bidRequestToErrors -> fillImplicitParameters(httpRequest, bidRequestToErrors, debugEnabled));
    }

    private AuctionContext updateContextWithDebugLog(AuctionContext auctionContext) {
        final HttpRequestContext requestContext = auctionContext.getHttpRequest();
        final boolean debugEnabled = isDebugEnabled(requestContext);
        final CachedDebugLog cachedDebugLog = new CachedDebugLog(debugEnabled, DEFAULT_CACHE_LOG_TTL,
                escapeLogCacheRegexPattern, mapper);
        if (cachedDebugLog.isEnabled()) {
            cachedDebugLog.setRequest(requestContext.getBody());
            cachedDebugLog.setHeadersLog(requestContext.getHeaders());
        }

        return auctionContext.toBuilder().cachedDebugLog(cachedDebugLog).build();
    }

    private boolean isDebugEnabled(HttpRequestContext requestContext) {
        final CaseInsensitiveMultiMap queryParams =
                ObjectUtil.getIfNotNull(requestContext, HttpRequestContext::getQueryParams);
        final String debugParam = ObjectUtil.getIfNotNull(queryParams, params -> params.get(DEBUG));
        return BooleanUtils.toBoolean(debugParam);
    }

    /**
     * Parses request body to {@link BidRequestVideo}.
     * <p>
     * Throws {@link InvalidRequestException} if body is empty, exceeds max request size or couldn't be deserialized.
     */
    private BidRequestVideo parseRequest(HttpRequestContext httpRequest) {
        try {
            final BidRequestVideo bidRequestVideo = mapper.decodeValue(httpRequest.getBody(), BidRequestVideo.class);
            return insertDeviceUa(httpRequest, bidRequestVideo);
        } catch (DecodeException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private BidRequestVideo insertDeviceUa(HttpRequestContext httpRequest, BidRequestVideo bidRequestVideo) {
        final Device device = bidRequestVideo.getDevice();
        final String deviceUa = device != null ? device.getUa() : null;
        if (StringUtils.isBlank(deviceUa)) {
            final String userAgentHeader = httpRequest.getHeaders().get(HttpUtil.USER_AGENT_HEADER);
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

    private static Set<String> podConfigIds(BidRequestVideo incomingBidRequest) {
        final Podconfig podconfig = incomingBidRequest.getPodconfig();
        if (podconfig != null && CollectionUtils.isNotEmpty(podconfig.getPods())) {
            return podconfig.getPods().stream()
                    .map(Pod::getConfigId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
        }

        return Collections.emptySet();
    }

    private static <T> T populatePodErrors(List<PodError> from, List<PodError> to, T returnObject) {
        to.addAll(from);
        return returnObject;
    }

    private WithPodErrors<BidRequest> fillImplicitParameters(HttpRequestContext httpRequest,
                                                             WithPodErrors<BidRequest> bidRequestToErrors,
                                                             boolean debugEnabled) {

        final BidRequest bidRequest = bidRequestToErrors.getData();
        final BidRequest updatedBidRequest = paramsResolver.resolve(
                bidRequest,
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .debugContext(DebugContext.empty())
                        .build(),
                ENDPOINT,
                false);
        final BidRequest updatedWithDebugBidRequest = debugEnabled
                ? updatedBidRequest.toBuilder().test(1).build()
                : updatedBidRequest;

        return WithPodErrors.of(updatedWithDebugBidRequest, bidRequestToErrors.getPodErrors());
    }

    private Future<WithPodErrors<BidRequest>> validateRequest(WithPodErrors<BidRequest> requestWithPodErrors,
                                                              HttpRequestContext httpRequestContext,
                                                              List<String> warnings) {

        return ortb2RequestFactory.validateRequest(requestWithPodErrors.getData(), httpRequestContext, warnings)
                .map(bidRequest -> requestWithPodErrors);
    }
}

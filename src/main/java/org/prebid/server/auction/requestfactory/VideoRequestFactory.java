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
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.VideoStoredRequestProcessor;
import org.prebid.server.auction.model.AuctionContext;
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
import java.util.stream.Collectors;

public class VideoRequestFactory {

    private final int maxRequestSize;
    private final boolean enforceStoredRequest;

    private final Ortb2RequestFactory ortb2RequestFactory;
    private final Ortb2ImplicitParametersResolver paramsResolver;
    private final VideoStoredRequestProcessor storedRequestProcessor;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final TimeoutResolver timeoutResolver;
    private final JacksonMapper mapper;

    public VideoRequestFactory(int maxRequestSize,
                               boolean enforceStoredRequest,
                               Ortb2RequestFactory ortb2RequestFactory,
                               Ortb2ImplicitParametersResolver paramsResolver,
                               VideoStoredRequestProcessor storedRequestProcessor,
                               PrivacyEnforcementService privacyEnforcementService,
                               TimeoutResolver timeoutResolver,
                               JacksonMapper mapper) {

        this.enforceStoredRequest = enforceStoredRequest;
        this.maxRequestSize = maxRequestSize;
        this.ortb2RequestFactory = Objects.requireNonNull(ortb2RequestFactory);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.mapper = Objects.requireNonNull(mapper);
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

        return createBidRequest(body, routingContext)
                .compose(bidRequestWithErrors -> ortb2RequestFactory.fetchAccountAndCreateAuctionContext(
                        routingContext,
                        bidRequestWithErrors.getData(),
                        MetricName.video,
                        false,
                        startTime,
                        new ArrayList<>())

                        .compose(auctionContext -> privacyEnforcementService.contextFromBidRequest(auctionContext)
                                .map(auctionContext::with))

                        .map(auctionContext -> auctionContext.with(
                                ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(
                                        auctionContext.getBidRequest(),
                                        auctionContext.getAccount(),
                                        auctionContext.getPrivacyContext())))

                        .map(auctionContext -> WithPodErrors.of(auctionContext, bidRequestWithErrors.getPodErrors())));
    }

    private String extractAndValidateBody(RoutingContext context) {
        final String body = context.getBodyAsString();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(String.format("Request size exceeded max size of %d bytes.",
                    maxRequestSize));
        }

        return body;
    }

    private Future<WithPodErrors<BidRequest>> createBidRequest(String body, RoutingContext routingContext) {

        final BidRequestVideo bidRequestVideo;
        try {
            bidRequestVideo = parseRequest(body, routingContext);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final String storedRequestId = bidRequestVideo.getStoredrequestid();
        if (StringUtils.isBlank(storedRequestId) && enforceStoredRequest) {
            return Future.failedFuture(new InvalidRequestException("Unable to find required stored request id"));
        }

        final Set<String> podConfigIds = podConfigIds(bidRequestVideo);

        return storedRequestProcessor.processVideoRequest(
                accountIdFrom(bidRequestVideo), storedRequestId, podConfigIds, bidRequestVideo)
                .map(bidRequestToErrors -> fillImplicitParametersAndValidate(routingContext, bidRequestToErrors));
    }

    /**
     * Parses request body to {@link BidRequestVideo}.
     * <p>
     * Throws {@link InvalidRequestException} if body is empty, exceeds max request size or couldn't be deserialized.
     */
    private BidRequestVideo parseRequest(String body, RoutingContext routingContext) {
        try {
            final BidRequestVideo bidRequestVideo = mapper.decodeValue(body, BidRequestVideo.class);
            return insertDeviceUa(routingContext, bidRequestVideo);
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

    private WithPodErrors<BidRequest> fillImplicitParametersAndValidate(RoutingContext routingContext,
                                                                        WithPodErrors<BidRequest> bidRequestToErrors) {
        final BidRequest bidRequest = bidRequestToErrors.getData();
        final BidRequest updatedBidRequest = paramsResolver.resolve(bidRequest, routingContext,
                timeoutResolver);
        final BidRequest validBidRequest = ortb2RequestFactory.validateRequest(updatedBidRequest);
        return WithPodErrors.of(validBidRequest, bidRequestToErrors.getPodErrors());
    }
}

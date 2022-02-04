package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.Account;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Used in OpenRTB request processing.
 */
public class AuctionRequestFactory {

    private final long maxRequestSize;
    private final Ortb2RequestFactory ortb2RequestFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ImplicitParametersExtractor paramsExtractor;
    private final Ortb2ImplicitParametersResolver paramsResolver;
    private final InterstitialProcessor interstitialProcessor;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final TimeoutResolver timeoutResolver;
    private final DebugResolver debugResolver;
    private final JacksonMapper mapper;
    private final OrtbTypesResolver ortbTypesResolver;

    private static final String ENDPOINT = Endpoint.openrtb2_auction.value();

    public AuctionRequestFactory(long maxRequestSize,
                                 Ortb2RequestFactory ortb2RequestFactory,
                                 StoredRequestProcessor storedRequestProcessor,
                                 ImplicitParametersExtractor paramsExtractor,
                                 Ortb2ImplicitParametersResolver paramsResolver,
                                 InterstitialProcessor interstitialProcessor,
                                 OrtbTypesResolver ortbTypesResolver,
                                 PrivacyEnforcementService privacyEnforcementService,
                                 TimeoutResolver timeoutResolver,
                                 DebugResolver debugResolver,
                                 JacksonMapper mapper) {

        this.maxRequestSize = maxRequestSize;
        this.ortb2RequestFactory = Objects.requireNonNull(ortb2RequestFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
        this.interstitialProcessor = Objects.requireNonNull(interstitialProcessor);
        this.ortbTypesResolver = Objects.requireNonNull(ortbTypesResolver);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final String body;
        try {
            body = extractAndValidateBody(routingContext);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final AuctionContext initialAuctionContext = ortb2RequestFactory.createAuctionContext(
                Endpoint.openrtb2_auction, MetricName.openrtb2web);

        return ortb2RequestFactory.executeEntrypointHooks(routingContext, body, initialAuctionContext)
                .compose(httpRequest -> parseBidRequest(httpRequest, initialAuctionContext.getPrebidErrors())
                        .map(bidRequest -> ortb2RequestFactory
                                .enrichAuctionContext(initialAuctionContext, httpRequest, bidRequest, startTime)
                                .with(requestTypeMetric(bidRequest))))

                .compose(auctionContext -> ortb2RequestFactory.fetchAccount(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(debugResolver.debugContextFrom(auctionContext)))

                .compose(auctionContext -> ortb2RequestFactory.executeRawAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> updateAndValidateBidRequest(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> privacyEnforcementService.contextFromBidRequest(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(
                        ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(auctionContext)))

                .compose(auctionContext -> ortb2RequestFactory.executeProcessedAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .compose(ortb2RequestFactory::populateDealsInfo)

                .recover(ortb2RequestFactory::restoreResultFromRejection);
    }

    private String extractAndValidateBody(RoutingContext routingContext) {
        final String body = routingContext.getBodyAsString();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        }

        return body;
    }

    private Future<BidRequest> parseBidRequest(HttpRequestContext httpRequest, List<String> errors) {
        try {
            final JsonNode bidRequestNode = bodyAsJsonNode(httpRequest.getBody());

            final String referer = paramsExtractor.refererFrom(httpRequest);
            ortbTypesResolver.normalizeBidRequest(bidRequestNode, errors, referer);

            return Future.succeededFuture(jsonNodeAsBidRequest(bidRequestNode));
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    private JsonNode bodyAsJsonNode(String body) {
        try {
            return mapper.mapper().readTree(body);
        } catch (IOException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest: %s", e.getMessage()));
        }
    }

    private BidRequest jsonNodeAsBidRequest(JsonNode bidRequestNode) {
        try {
            return mapper.mapper().treeToValue(bidRequestNode, BidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest: %s", e.getMessage()));
        }
    }

    /**
     * Sets {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> updateAndValidateBidRequest(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final HttpRequestContext httpRequest = auctionContext.getHttpRequest();

        return storedRequestProcessor.processStoredRequests(account.getId(), bidRequest)
                .map(resolvedBidRequest ->
                        paramsResolver.resolve(resolvedBidRequest, httpRequest, timeoutResolver, ENDPOINT))

                .compose(resolvedBidRequest ->
                        ortb2RequestFactory.validateRequest(resolvedBidRequest, auctionContext.getDebugWarnings()))

                .map(interstitialProcessor::process);
    }

    private static MetricName requestTypeMetric(BidRequest bidRequest) {
        return bidRequest.getApp() != null ? MetricName.openrtb2app : MetricName.openrtb2web;
    }
}

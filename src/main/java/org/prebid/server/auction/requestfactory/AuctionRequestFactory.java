package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
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
import org.prebid.server.settings.model.Account;

import java.io.IOException;
import java.util.ArrayList;
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
    private final JacksonMapper mapper;
    private final OrtbTypesResolver ortbTypesResolver;

    public AuctionRequestFactory(long maxRequestSize,
                                 Ortb2RequestFactory ortb2RequestFactory,
                                 StoredRequestProcessor storedRequestProcessor,
                                 ImplicitParametersExtractor paramsExtractor,
                                 Ortb2ImplicitParametersResolver paramsResolver,
                                 InterstitialProcessor interstitialProcessor,
                                 OrtbTypesResolver ortbTypesResolver,
                                 PrivacyEnforcementService privacyEnforcementService,
                                 TimeoutResolver timeoutResolver,
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
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final List<String> errors = new ArrayList<>();
        final String body;
        try {
            body = extractAndValidateBody(routingContext);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        return parseBidRequest(body, routingContext, errors)
                .compose(bidRequest -> ortb2RequestFactory.fetchAccountAndCreateAuctionContext(
                        routingContext,
                        bidRequest,
                        requestTypeMetric(bidRequest),
                        true,
                        startTime,
                        errors))

                .compose(auctionContext -> updateBidRequest(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> privacyEnforcementService.contextFromBidRequest(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(
                        ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(
                                auctionContext.getBidRequest(),
                                auctionContext.getAccount(),
                                auctionContext.getPrivacyContext())));
    }

    private String extractAndValidateBody(RoutingContext context) {
        final String body = context.getBodyAsString();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        }

        return body;
    }

    private Future<BidRequest> parseBidRequest(String body, RoutingContext context, List<String> errors) {
        try {
            final JsonNode bidRequestNode = bodyAsJsonNode(body);

            final String referer = paramsExtractor.refererFrom(context.request());
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
    private Future<BidRequest> updateBidRequest(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final RoutingContext routingContext = auctionContext.getRoutingContext();

        return storedRequestProcessor.processStoredRequests(account.getId(), bidRequest)
                .map(resolvedBidRequest -> paramsResolver.resolve(resolvedBidRequest, routingContext, timeoutResolver))
                .map(ortb2RequestFactory::validateRequest)
                .map(interstitialProcessor::process);
    }

    private static MetricName requestTypeMetric(BidRequest bidRequest) {
        return bidRequest.getApp() != null ? MetricName.openrtb2app : MetricName.openrtb2web;
    }
}

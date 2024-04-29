package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.gpp.AuctionGppService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionStoredResult;
import org.prebid.server.auction.privacy.contextfactory.AuctionPrivacyContextFactory;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.cookie.CookieDeprecationService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.settings.model.Account;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Used in OpenRTB request processing.
 */
public class AuctionRequestFactory {

    private final long maxRequestSize;
    private final Ortb2RequestFactory ortb2RequestFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    private final AuctionGppService gppService;
    private final CookieDeprecationService cookieDeprecationService;
    private final ImplicitParametersExtractor paramsExtractor;
    private final Ortb2ImplicitParametersResolver paramsResolver;
    private final InterstitialProcessor interstitialProcessor;
    private final AuctionPrivacyContextFactory auctionPrivacyContextFactory;
    private final DebugResolver debugResolver;
    private final JacksonMapper mapper;
    private final OrtbTypesResolver ortbTypesResolver;
    private final GeoLocationServiceWrapper geoLocationServiceWrapper;

    private static final String ENDPOINT = Endpoint.openrtb2_auction.value();

    public AuctionRequestFactory(long maxRequestSize,
                                 Ortb2RequestFactory ortb2RequestFactory,
                                 StoredRequestProcessor storedRequestProcessor,
                                 BidRequestOrtbVersionConversionManager ortbVersionConversionManager,
                                 AuctionGppService gppService,
                                 CookieDeprecationService cookieDeprecationService,
                                 ImplicitParametersExtractor paramsExtractor,
                                 Ortb2ImplicitParametersResolver paramsResolver,
                                 InterstitialProcessor interstitialProcessor,
                                 OrtbTypesResolver ortbTypesResolver,
                                 AuctionPrivacyContextFactory auctionPrivacyContextFactory,
                                 DebugResolver debugResolver,
                                 JacksonMapper mapper,
                                 GeoLocationServiceWrapper geoLocationServiceWrapper) {

        this.maxRequestSize = maxRequestSize;
        this.ortb2RequestFactory = Objects.requireNonNull(ortb2RequestFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.ortbVersionConversionManager = Objects.requireNonNull(ortbVersionConversionManager);
        this.gppService = Objects.requireNonNull(gppService);
        this.cookieDeprecationService = Objects.requireNonNull(cookieDeprecationService);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
        this.interstitialProcessor = Objects.requireNonNull(interstitialProcessor);
        this.ortbTypesResolver = Objects.requireNonNull(ortbTypesResolver);
        this.auctionPrivacyContextFactory = Objects.requireNonNull(auctionPrivacyContextFactory);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.mapper = Objects.requireNonNull(mapper);
        this.geoLocationServiceWrapper = Objects.requireNonNull(geoLocationServiceWrapper);
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

                .compose(auctionContext -> geoLocationServiceWrapper.lookup(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithGeolocationData(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> gppService.contextFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.activityInfrastructureFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.executeRawAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> updateAndValidateBidRequest(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> auctionPrivacyContextFactory.contextFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.executeProcessedAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .map(ortb2RequestFactory::enrichWithPriceFloors)

                .map(ortb2RequestFactory::updateTimeout)

                .recover(ortb2RequestFactory::restoreResultFromRejection);
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

    private Future<BidRequest> parseBidRequest(HttpRequestContext httpRequest, List<String> errors) {
        try {
            final JsonNode bidRequestNode = bodyAsJsonNode(httpRequest.getBody());

            final String referer = paramsExtractor.refererFrom(httpRequest);
            ortbTypesResolver.normalizeBidRequest(bidRequestNode, errors, referer);

            return Future.succeededFuture(jsonNodeAsBidRequest(bidRequestNode))
                    .map(bidRequest -> fillWithValuesFromHttpRequest(bidRequest, httpRequest));
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    private JsonNode bodyAsJsonNode(String body) {
        try {
            return mapper.mapper().readTree(body);
        } catch (IOException e) {
            throw new InvalidRequestException("Error decoding bidRequest: " + e.getMessage());
        }
    }

    private BidRequest jsonNodeAsBidRequest(JsonNode bidRequestNode) {
        try {
            return mapper.mapper().treeToValue(bidRequestNode, BidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Error decoding bidRequest: " + e.getMessage());
        }
    }

    private BidRequest fillWithValuesFromHttpRequest(BidRequest bidRequest, HttpRequestContext httpRequest) {
        return !containsRegsExtGpc(bidRequest)
                ? bidRequest.toBuilder()
                .regs(fillRegsWithValuesFromHttpRequest(bidRequest.getRegs(), httpRequest))
                .build()
                : bidRequest;
    }

    private boolean containsRegsExtGpc(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getRegs())
                .map(Regs::getExt)
                .map(ExtRegs::getGpc)
                .isPresent();
    }

    private Regs fillRegsWithValuesFromHttpRequest(Regs regs, HttpRequestContext httpRequest) {
        final String gpc = paramsExtractor.gpcFrom(httpRequest);
        if (gpc == null) {
            return regs;
        }

        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        return (regs != null ? regs.toBuilder() : Regs.builder())
                .ext(ExtRegs.of(
                        extRegs != null ? extRegs.getGdpr() : null,
                        extRegs != null ? extRegs.getUsPrivacy() : null,
                        gpc,
                        extRegs != null ? extRegs.getDsa() : null))
                .build();
    }

    /**
     * Sets {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> updateAndValidateBidRequest(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final HttpRequestContext httpRequest = auctionContext.getHttpRequest();
        final List<String> debugWarnings = auctionContext.getDebugWarnings();

        return storedRequestProcessor.processAuctionRequest(account.getId(), auctionContext.getBidRequest())
                .compose(auctionStoredResult -> updateBidRequest(auctionStoredResult, auctionContext))
                .compose(bidRequest -> ortb2RequestFactory.validateRequest(bidRequest, httpRequest, debugWarnings))
                .map(interstitialProcessor::process);
    }

    private Future<BidRequest> updateBidRequest(AuctionStoredResult auctionStoredResult,
                                                AuctionContext auctionContext) {

        final boolean hasStoredBidRequest = auctionStoredResult.hasStoredBidRequest();

        return Future.succeededFuture(auctionStoredResult.bidRequest())
                .map(ortbVersionConversionManager::convertToAuctionSupportedVersion)
                .map(bidRequest -> gppService.updateBidRequest(bidRequest, auctionContext))
                .map(bidRequest -> paramsResolver.resolve(bidRequest, auctionContext, ENDPOINT, hasStoredBidRequest))
                .map(bidRequest -> cookieDeprecationService.updateBidRequestDevice(bidRequest, auctionContext));
    }

    private static MetricName requestTypeMetric(BidRequest bidRequest) {
        if (bidRequest.getApp() != null) {
            return MetricName.openrtb2app;
        } else if (bidRequest.getDooh() != null) {
            return MetricName.openrtb2dooh;
        } else {
            return MetricName.openrtb2web;
        }
    }
}

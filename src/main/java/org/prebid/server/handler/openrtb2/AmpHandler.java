package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.model.Tuple3;
import org.prebid.server.auction.requestfactory.AmpRequestFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.response.AmpResponse;
import org.prebid.server.proto.response.ExtAmpVideoPrebid;
import org.prebid.server.proto.response.ExtAmpVideoResponse;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AmpHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AmpHandler.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    public static final String PREBID_EXT = "prebid";
    private static final MetricName REQUEST_TYPE_METRIC = MetricName.amp;

    private final AmpRequestFactory ampRequestFactory;
    private final ExchangeService exchangeService;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final Clock clock;
    private final BidderCatalog bidderCatalog;
    private final Set<String> biddersSupportingCustomTargeting;
    private final AmpResponsePostProcessor ampResponsePostProcessor;
    private final HttpInteractionLogger httpInteractionLogger;
    private final JacksonMapper mapper;

    public AmpHandler(AmpRequestFactory ampRequestFactory,
                      ExchangeService exchangeService,
                      AnalyticsReporterDelegator analyticsDelegator,
                      Metrics metrics,
                      Clock clock,
                      BidderCatalog bidderCatalog,
                      Set<String> biddersSupportingCustomTargeting,
                      AmpResponsePostProcessor ampResponsePostProcessor,
                      HttpInteractionLogger httpInteractionLogger,
                      JacksonMapper mapper) {

        this.ampRequestFactory = Objects.requireNonNull(ampRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.biddersSupportingCustomTargeting = Objects.requireNonNull(biddersSupportingCustomTargeting);
        this.ampResponsePostProcessor = Objects.requireNonNull(ampResponsePostProcessor);
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final AmpEvent.AmpEventBuilder ampEventBuilder = AmpEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        ampRequestFactory.fromRequest(routingContext, startTime)

                .map(context -> addToEvent(context, ampEventBuilder::auctionContext, context))
                .map(this::updateAppAndNoCookieAndImpsMetrics)

                .compose(context -> exchangeService.holdAuction(context)
                        .map(bidResponse -> Tuple2.of(bidResponse, context)))

                .map((Tuple2<BidResponse, AuctionContext> result) ->
                        addToEvent(result.getLeft(), ampEventBuilder::bidResponse, result))
                .map((Tuple2<BidResponse, AuctionContext> result) ->
                        Tuple3.of(
                                result.getLeft(),
                                result.getRight(),
                                toAmpResponse(result.getRight(), result.getLeft())))

                .compose((Tuple3<BidResponse, AuctionContext, AmpResponse> result) ->
                        ampResponsePostProcessor.postProcess(
                                result.getMiddle().getBidRequest(),
                                result.getLeft(),
                                result.getRight(),
                                routingContext)
                                .map(ampResponse -> Tuple3.of(result.getLeft(), result.getMiddle(), ampResponse)))

                .map((Tuple3<BidResponse, AuctionContext, AmpResponse> result) ->
                        addToEvent(result.getRight().getTargeting(), ampEventBuilder::targeting, result))
                .setHandler(responseResult -> handleResult(responseResult, ampEventBuilder, routingContext, startTime));
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private AuctionContext updateAppAndNoCookieAndImpsMetrics(AuctionContext context) {
        if (!context.isRequestRejected()) {
            final BidRequest bidRequest = context.getBidRequest();
            final UidsCookie uidsCookie = context.getUidsCookie();

            final List<Imp> imps = bidRequest.getImp();
            metrics.updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest.getApp() != null, uidsCookie.hasLiveUids(),
                    imps.size());

            metrics.updateImpTypesMetrics(imps);
        }

        return context;
    }

    private AmpResponse toAmpResponse(AuctionContext auctionContext, BidResponse bidResponse) {
        // Fetch targeting information from response bids
        final List<SeatBid> seatBids = bidResponse.getSeatbid();

        final Map<String, JsonNode> targeting = seatBids == null ? Collections.emptyMap() : seatBids.stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream()
                        .filter(Objects::nonNull)
                        .flatMap(bid -> targetingFrom(bid, seatBid.getSeat()).entrySet().stream()))
                .map(entry -> Tuple2.of(entry.getKey(), TextNode.valueOf(entry.getValue())))
                .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight, (value1, value2) -> value2));

        final ExtResponseDebug extResponseDebug;
        final Map<String, List<ExtBidderError>> errors;
        // Fetch debug and errors information from response if requested
        if (auctionContext.getDebugContext().isDebugEnabled()) {
            final ExtBidResponse extBidResponse = bidResponse.getExt();

            extResponseDebug = extBidResponse != null ? extBidResponse.getDebug() : null;
            errors = extBidResponse != null ? extBidResponse.getErrors() : null;
        } else {
            extResponseDebug = null;
            errors = null;
        }

        return AmpResponse.of(targeting, extResponseDebug, errors, extResponseFrom(bidResponse));
    }

    private Map<String, String> targetingFrom(Bid bid, String bidder) {
        final ObjectNode bidExt = bid.getExt();
        if (bidExt == null || !bidExt.hasNonNull(PREBID_EXT)) {
            return Collections.emptyMap();
        }

        final ExtBidPrebid extBidPrebid;
        try {
            extBidPrebid = mapper.mapper().convertValue(bidExt.get(PREBID_EXT), ExtBidPrebid.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Critical error while unpacking AMP targets: %s", e.getMessage()), e);
        }

        // Need to extract the targeting parameters from the response, as those are all that
        // go in the AMP response
        final Map<String, String> targeting = extBidPrebid != null ? extBidPrebid.getTargeting() : null;
        if (targeting != null && targeting.keySet().stream()
                .anyMatch(key -> key != null && key.startsWith("hb_cache_id"))) {

            return enrichWithCustomTargeting(targeting, bidExt, bidder);
        }

        return Collections.emptyMap();
    }

    private Map<String, String> enrichWithCustomTargeting(
            Map<String, String> targeting, ObjectNode bidExt, String bidder) {

        final Map<String, String> customTargeting = customTargetingFrom(bidExt, bidder);
        if (!customTargeting.isEmpty()) {
            final Map<String, String> enrichedTargeting = new HashMap<>(targeting);
            enrichedTargeting.putAll(customTargeting);
            return enrichedTargeting;
        }
        return targeting;
    }

    private Map<String, String> customTargetingFrom(ObjectNode extBidBidder, String bidder) {
        if (extBidBidder != null && biddersSupportingCustomTargeting.contains(bidder)
                && bidderCatalog.isValidName(bidder)) {

            return bidderCatalog.bidderByName(bidder).extractTargeting(extBidBidder);
        } else {
            return Collections.emptyMap();
        }
    }

    private static ExtAmpVideoResponse extResponseFrom(BidResponse bidResponse) {
        final ExtBidResponse ext = bidResponse.getExt();
        final ExtBidResponsePrebid extPrebid = ext != null ? ext.getPrebid() : null;
        final ExtModules extModules = extPrebid != null ? extPrebid.getModules() : null;

        return extModules != null ? ExtAmpVideoResponse.of(ExtAmpVideoPrebid.of(extModules)) : null;
    }

    private void handleResult(AsyncResult<Tuple3<BidResponse, AuctionContext, AmpResponse>> responseResult,
                              AmpEvent.AmpEventBuilder ampEventBuilder,
                              RoutingContext routingContext,
                              long startTime) {

        final boolean responseSucceeded = responseResult.succeeded();
        final AuctionContext auctionContext = responseSucceeded ? responseResult.result().getMiddle() : null;

        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final HttpResponseStatus status;
        final String body;

        final String origin = originFrom(routingContext);
        ampEventBuilder.origin(origin);

        // Add AMP headers
        routingContext.response().headers()
                .add("AMP-Access-Control-Allow-Source-Origin", origin)
                .add("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");

        if (responseSucceeded) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();

            status = HttpResponseStatus.OK;
            routingContext.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
            body = mapper.encode(responseResult.result().getRight());
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;

                final InvalidRequestException invalidRequestException = (InvalidRequestException) exception;
                errorMessages = invalidRequestException.getMessages().stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.toList());
                final String message = String.join("\n", errorMessages);

                conditionalLogger.info(String.format("%s, Referer: %s", message,
                        routingContext.request().headers().get(HttpUtil.REFERER_HEADER)), 100);

                status = HttpResponseStatus.BAD_REQUEST;
                body = message;
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String message = exception.getMessage();
                conditionalLogger.info(message, 100);

                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.UNAUTHORIZED;
                body = message;
                String accountId = ((UnauthorizedAccountException) exception).getAccountId();
                metrics.updateAccountRequestRejectedMetrics(accountId);
            } else if (exception instanceof BlacklistedAppException
                    || exception instanceof BlacklistedAccountException) {
                metricRequestStatus = exception instanceof BlacklistedAccountException
                        ? MetricName.blacklisted_account : MetricName.blacklisted_app;
                final String message = String.format("Blacklisted: %s", exception.getMessage());
                logger.debug(message);

                errorMessages = Collections.singletonList(message);
                status = HttpResponseStatus.FORBIDDEN;
                body = message;
            } else {
                final String message = exception.getMessage();

                metricRequestStatus = MetricName.err;
                errorMessages = Collections.singletonList(message);
                logger.error("Critical error while running the auction", exception);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                body = String.format("Critical error while running the auction: %s", message);
            }
        }

        final int statusCode = status.code();
        final AmpEvent ampEvent = ampEventBuilder.status(statusCode).errors(errorMessages).build();

        final PrivacyContext privacyContext = auctionContext != null ? auctionContext.getPrivacyContext() : null;
        final TcfContext tcfContext = privacyContext != null ? privacyContext.getTcfContext() : TcfContext.empty();
        respondWith(routingContext, status, body, startTime, metricRequestStatus, ampEvent, tcfContext);

        httpInteractionLogger.maybeLogOpenrtb2Amp(auctionContext, routingContext, statusCode, body);
    }

    private static String originFrom(RoutingContext routingContext) {
        String origin = null;
        final List<String> ampSourceOrigin = routingContext.queryParam("__amp_source_origin");
        if (CollectionUtils.isNotEmpty(ampSourceOrigin)) {
            origin = ampSourceOrigin.get(0);
        }
        if (origin == null) {
            // Just to be safe
            origin = ObjectUtils.defaultIfNull(routingContext.request().headers().get("Origin"), StringUtils.EMPTY);
        }
        return origin;
    }

    private void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body, long startTime,
                             MetricName metricRequestStatus, AmpEvent event, TcfContext tcfContext) {

        final boolean responseSent = HttpUtil.executeSafely(routingContext, Endpoint.openrtb2_amp,
                response -> response
                        .exceptionHandler(this::handleResponseException)
                        .setStatusCode(status.code())
                        .end(body));

        if (responseSent) {
            metrics.updateRequestTimeMetric(clock.millis() - startTime);
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, metricRequestStatus);
            analyticsDelegator.processEvent(event, tcfContext);
        } else {
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
        }
    }

    private void handleResponseException(Throwable exception) {
        logger.warn("Failed to send amp response: {0}", exception.getMessage());
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
    }
}

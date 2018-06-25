package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.model.Tuple3;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.model.MetricsContext;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.response.AmpResponse;
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

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>>() {
            };
    private static final TypeReference<ExtBidResponse> EXT_BID_RESPONSE_TYPE_REFERENCE =
            new TypeReference<ExtBidResponse>() {
            };
    private static final MetricsContext METRICS_CONTEXT = MetricsContext.of(MetricName.amp);

    private final long defaultTimeout;
    private final AmpRequestFactory ampRequestFactory;
    private final ExchangeService exchangeService;
    private final UidsCookieService uidsCookieService;
    private final Set<String> biddersSupportingCustomTargeting;
    private final BidderCatalog bidderCatalog;
    private final AnalyticsReporter analyticsReporter;
    private final AmpResponsePostProcessor ampResponsePostProcessor;
    private final Metrics metrics;
    private final Clock clock;
    private final TimeoutFactory timeoutFactory;

    public AmpHandler(long defaultTimeout, AmpRequestFactory ampRequestFactory, ExchangeService exchangeService,
                      UidsCookieService uidsCookieService, Set<String> biddersSupportingCustomTargeting,
                      BidderCatalog bidderCatalog, AnalyticsReporter analyticsReporter,
                      AmpResponsePostProcessor ampResponsePostProcessor, Metrics metrics, Clock clock,
                      TimeoutFactory timeoutFactory) {
        this.defaultTimeout = defaultTimeout;
        this.ampRequestFactory = Objects.requireNonNull(ampRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.biddersSupportingCustomTargeting = Objects.requireNonNull(biddersSupportingCustomTargeting);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.ampResponsePostProcessor = Objects.requireNonNull(ampResponsePostProcessor);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
    }

    @Override
    public void handle(RoutingContext context) {
        final AmpEvent.AmpEventBuilder ampEventBuilder = AmpEvent.builder();

        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final boolean isSafari = HttpUtil.isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));

        updateSafariMetrics(isSafari);

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);

        ampRequestFactory.fromRequest(context)
                .map(bidRequest ->
                        updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest, uidsCookie.hasLiveUids(), isSafari))
                .compose(bidRequest ->
                        exchangeService.holdAuction(bidRequest, uidsCookie, timeout(bidRequest, startTime),
                                METRICS_CONTEXT)
                                .map(bidResponse -> Tuple2.of(bidRequest, bidResponse)))
                .map((Tuple2<BidRequest, BidResponse> result) ->
                        addToEvent(result.getRight(), ampEventBuilder::bidResponse, result))
                .map((Tuple2<BidRequest, BidResponse> result) -> Tuple3.of(result.getLeft(), result.getRight(),
                        toAmpResponse(result.getLeft(), result.getRight())))
                .compose((Tuple3<BidRequest, BidResponse, AmpResponse> result) ->
                        ampResponsePostProcessor.postProcess(result.getLeft(), result.getMiddle(), result.getRight(),
                                context.queryParams()))
                .map(ampResponse -> addToEvent(ampResponse.getTargeting(), ampEventBuilder::targeting, ampResponse))
                .map(ampResponse -> setupRequestTimeMetricUpdater(ampResponse, context, startTime))
                .setHandler(responseResult -> handleResult(responseResult, ampEventBuilder, context));
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private void updateSafariMetrics(boolean isSafari) {
        if (isSafari) {
            metrics.incCounter(MetricName.safari_requests);
        }
    }

    private BidRequest updateAppAndNoCookieAndImpsRequestedMetrics(BidRequest bidRequest, boolean liveUidsPresent,
                                                                   boolean isSafari) {
        if (bidRequest.getApp() != null) {
            metrics.incCounter(MetricName.app_requests);
        } else if (!liveUidsPresent) {
            metrics.incCounter(MetricName.no_cookie_requests);
            if (isSafari) {
                metrics.incCounter(MetricName.safari_no_cookie_requests);
            }
        }
        metrics.incCounter(MetricName.imps_requested, bidRequest.getImp().size());
        return bidRequest;
    }

    private Timeout timeout(BidRequest bidRequest, long startTime) {
        final Long tmax = bidRequest.getTmax();
        return timeoutFactory.create(startTime, tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }

    private <T> T setupRequestTimeMetricUpdater(T returnValue, RoutingContext context, long startTime) {
        // set up handler to update request time metric when response is sent back to a client
        context.response().endHandler(ignored ->
                metrics.updateTimer(MetricName.request_time, clock.millis() - startTime));
        return returnValue;
    }

    private AmpResponse toAmpResponse(BidRequest bidRequest, BidResponse bidResponse) {
        // fetch targeting information from response bids
        final List<SeatBid> seatBids = bidResponse.getSeatbid();

        final Map<String, JsonNode> targeting = seatBids == null ? Collections.emptyMap() : seatBids.stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream()
                        .filter(Objects::nonNull)
                        .flatMap(bid -> targetingFrom(bid, seatBid.getSeat()).entrySet().stream()))
                .map(entry -> Tuple2.of(entry.getKey(), TextNode.valueOf(entry.getValue())))
                .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight));

        // fetch debug information from response if requested
        final ExtResponseDebug extResponseDebug = Objects.equals(bidRequest.getTest(), 1)
                ? extResponseDebugFrom(bidResponse) : null;

        return AmpResponse.of(targeting, extResponseDebug);
    }

    private Map<String, String> targetingFrom(Bid bid, String bidder) {
        final ExtPrebid<ExtBidPrebid, ObjectNode> extBid;
        try {
            extBid = Json.mapper.convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Critical error while unpacking AMP targets: %s", e.getMessage()), e);
        }

        if (extBid != null) {
            final ExtBidPrebid extBidPrebid = extBid.getPrebid();

            // Need to extract the targeting parameters from the response, as those are all that
            // go in the AMP response
            final Map<String, String> targeting = extBidPrebid != null ? extBidPrebid.getTargeting() : null;
            if (targeting != null && targeting.keySet().stream()
                    .anyMatch(key -> key != null && key.startsWith("hb_cache_id"))) {

                return enrichWithCustomTargeting(targeting, extBid, bidder);
            }
        }

        return Collections.emptyMap();
    }

    private Map<String, String> enrichWithCustomTargeting(
            Map<String, String> targeting, ExtPrebid<ExtBidPrebid, ObjectNode> extBid, String bidder) {

        final Map<String, String> customTargeting = customTargetingFrom(extBid.getBidder(), bidder);
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

    private static ExtResponseDebug extResponseDebugFrom(BidResponse bidResponse) {
        final ExtBidResponse extBidResponse;
        try {
            extBidResponse = Json.mapper.convertValue(bidResponse.getExt(), EXT_BID_RESPONSE_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Critical error while unpacking AMP bid response: %s", e.getMessage()), e);
        }
        return extBidResponse != null ? extBidResponse.getDebug() : null;
    }

    private void handleResult(AsyncResult<AmpResponse> responseResult, AmpEvent.AmpEventBuilder ampEventBuilder,
                              RoutingContext context) {
        final MetricName requestStatus;
        final int status;
        final List<String> errorMessages;

        final String origin = originFrom(context);

        ampEventBuilder.origin(origin);

        // Add AMP headers
        context.response()
                .putHeader("AMP-Access-Control-Allow-Source-Origin", origin)
                .putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        if (responseResult.succeeded()) {
            context.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            context.response().end(Json.encode(responseResult.result()));

            requestStatus = MetricName.ok;
            status = HttpResponseStatus.OK.code();
            errorMessages = Collections.emptyList();
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                requestStatus = MetricName.badinput;
                status = HttpResponseStatus.BAD_REQUEST.code();
                errorMessages = ((InvalidRequestException) exception).getMessages();

                logger.info("Invalid request format: {0}", errorMessages);

                context.response()
                        .setStatusCode(status)
                        .end(errorMessages.stream().map(msg -> String.format("Invalid request format: %s", msg))
                                .collect(Collectors.joining("\n")));
            } else {
                logger.error("Critical error while running the auction", exception);

                requestStatus = MetricName.err;
                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                context.response()
                        .setStatusCode(status)
                        .end(String.format("Critical error while running the auction: %s", message));
            }
        }

        updateRequestMetric(METRICS_CONTEXT.getRequestType(), requestStatus);
        analyticsReporter.processEvent(ampEventBuilder.status(status).errors(errorMessages).build());
    }

    private void updateRequestMetric(MetricName requestType, MetricName requestStatus) {
        metrics.forRequestType(requestType).incCounter(requestStatus);
    }

    private static String originFrom(RoutingContext context) {
        String origin = null;
        final List<String> ampSourceOrigin = context.queryParam("__amp_source_origin");
        if (CollectionUtils.isNotEmpty(ampSourceOrigin)) {
            origin = ampSourceOrigin.get(0);
        }
        if (origin == null) {
            // Just to be safe
            origin = ObjectUtils.firstNonNull(context.request().headers().get("Origin"), StringUtils.EMPTY);
        }
        return origin;
    }
}

package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.model.Tuple3;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.manager.AdminManager;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
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
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>>() {
            };
    private static final TypeReference<ExtBidResponse> EXT_BID_RESPONSE_TYPE_REFERENCE =
            new TypeReference<ExtBidResponse>() {
            };
    private static final MetricName REQUEST_TYPE_METRIC = MetricName.amp;

    private final AmpRequestFactory ampRequestFactory;
    private final ExchangeService exchangeService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;
    private final BidderCatalog bidderCatalog;
    private final Set<String> biddersSupportingCustomTargeting;
    private final AmpResponsePostProcessor ampResponsePostProcessor;
    private final AdminManager adminManager;
    private final JacksonMapper mapper;

    public AmpHandler(AmpRequestFactory ampRequestFactory,
                      ExchangeService exchangeService,
                      AnalyticsReporter analyticsReporter,
                      Metrics metrics,
                      Clock clock,
                      BidderCatalog bidderCatalog,
                      Set<String> biddersSupportingCustomTargeting,
                      AmpResponsePostProcessor ampResponsePostProcessor,
                      AdminManager adminManager,
                      JacksonMapper mapper) {

        this.ampRequestFactory = Objects.requireNonNull(ampRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.biddersSupportingCustomTargeting = Objects.requireNonNull(biddersSupportingCustomTargeting);
        this.ampResponsePostProcessor = Objects.requireNonNull(ampResponsePostProcessor);
        this.adminManager = Objects.requireNonNull(adminManager);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final boolean isSafari = HttpUtil.isSafari(routingContext.request().headers().get(HttpUtil.USER_AGENT_HEADER));
        metrics.updateSafariRequestsMetric(isSafari);

        final AmpEvent.AmpEventBuilder ampEventBuilder = AmpEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        ampRequestFactory.fromRequest(routingContext, startTime)
                .map(context -> context.toBuilder()
                        .requestTypeMetric(REQUEST_TYPE_METRIC)
                        .build())

                .map(context -> addToEvent(context, ampEventBuilder::auctionContext, context))
                .map(context -> updateAppAndNoCookieAndImpsMetrics(context, isSafari))

                .compose(context -> exchangeService.holdAuction(context)
                        .map(bidResponse -> Tuple2.of(bidResponse, context)))

                .map(result -> addToEvent(result.getLeft(), ampEventBuilder::bidResponse, result))
                .map(result -> Tuple3.of(result.getLeft(), result.getRight(),
                        toAmpResponse(result.getRight().getBidRequest(), result.getLeft())))

                .compose(result -> ampResponsePostProcessor.postProcess(result.getMiddle().getBidRequest(),
                        result.getLeft(), result.getRight(), routingContext))

                .map(ampResponse -> addToEvent(ampResponse.getTargeting(), ampEventBuilder::targeting, ampResponse))
                .setHandler(responseResult -> handleResult(responseResult, ampEventBuilder, routingContext, startTime));
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private AuctionContext updateAppAndNoCookieAndImpsMetrics(AuctionContext context, boolean isSafari) {
        final BidRequest bidRequest = context.getBidRequest();
        final UidsCookie uidsCookie = context.getUidsCookie();

        final List<Imp> imps = bidRequest.getImp();
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest.getApp() != null, uidsCookie.hasLiveUids(),
                isSafari, imps.size());

        metrics.updateImpTypesMetrics(imps);

        return context;
    }

    private AmpResponse toAmpResponse(BidRequest bidRequest, BidResponse bidResponse) {
        // Fetch targeting information from response bids
        final List<SeatBid> seatBids = bidResponse.getSeatbid();

        final Map<String, JsonNode> targeting = seatBids == null ? Collections.emptyMap() : seatBids.stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream()
                        .filter(Objects::nonNull)
                        .flatMap(bid -> targetingFrom(bid, seatBid.getSeat()).entrySet().stream()))
                .map(entry -> Tuple2.of(entry.getKey(), TextNode.valueOf(entry.getValue())))
                .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight));

        final ExtResponseDebug extResponseDebug;
        final Map<String, List<ExtBidderError>> errors;
        // Fetch debug and errors information from response if requested
        if (isDebugEnabled(bidRequest)) {
            final ExtBidResponse extBidResponse = extResponseFrom(bidResponse);

            extResponseDebug = extResponseDebugFrom(extBidResponse);
            errors = errorsFrom(extBidResponse);
        } else {
            extResponseDebug = null;
            errors = null;
        }

        return AmpResponse.of(targeting, extResponseDebug, errors);
    }

    private Map<String, String> targetingFrom(Bid bid, String bidder) {
        final ExtPrebid<ExtBidPrebid, ObjectNode> extBid;
        try {
            extBid = mapper.mapper().convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE);
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

    /**
     * Determines debug flag from {@link BidRequest}.
     */
    private boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }
        final ExtBidRequest extBidRequest = extBidRequestFrom(bidRequest);
        final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
    }

    /**
     * Extracts {@link ExtBidRequest} from {@link BidRequest}.
     */
    private ExtBidRequest extBidRequestFrom(BidRequest bidRequest) {
        try {
            return bidRequest.getExt() != null
                    ? mapper.mapper().treeToValue(bidRequest.getExt(), ExtBidRequest.class)
                    : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()), e);
        }
    }

    private ExtBidResponse extResponseFrom(BidResponse bidResponse) {
        try {
            return mapper.mapper().convertValue(bidResponse.getExt(), EXT_BID_RESPONSE_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Critical error while unpacking AMP bid response: %s", e.getMessage()), e);
        }
    }

    private static ExtResponseDebug extResponseDebugFrom(ExtBidResponse extBidResponse) {
        return extBidResponse != null ? extBidResponse.getDebug() : null;
    }

    private static Map<String, List<ExtBidderError>> errorsFrom(ExtBidResponse extBidResponse) {
        return extBidResponse != null ? extBidResponse.getErrors() : null;
    }

    private void handleResult(AsyncResult<AmpResponse> responseResult, AmpEvent.AmpEventBuilder ampEventBuilder,
                              RoutingContext context, long startTime) {
        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final int status;
        final String body;

        final String origin = originFrom(context);
        ampEventBuilder.origin(origin);

        // Add AMP headers
        context.response().headers()
                .add("AMP-Access-Control-Allow-Source-Origin", origin)
                .add("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");

        if (responseResult.succeeded()) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();

            status = HttpResponseStatus.OK.code();
            context.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
            body = mapper.encode(responseResult.result());
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;

                final InvalidRequestException invalidRequestException = (InvalidRequestException) exception;
                errorMessages = invalidRequestException.getMessages().stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.toList());
                final String message = String.join("\n", errorMessages);

                // TODO adminManager: enable when admin endpoints can be bound on application port
                //adminManager.accept(AdminManager.COUNTER_KEY, logger,
                //        logMessageFrom(invalidRequestException, message, context));
                conditionalLogger.info(String.format("%s, Referer: %s", message,
                        context.request().headers().get(HttpUtil.REFERER_HEADER)), 100);

                status = HttpResponseStatus.BAD_REQUEST.code();
                body = message;
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String message = exception.getMessage();
                conditionalLogger.info(message, 100);

                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.UNAUTHORIZED.code();
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
                status = HttpResponseStatus.FORBIDDEN.code();
                body = message;
            } else {
                final String message = exception.getMessage();

                metricRequestStatus = MetricName.err;
                errorMessages = Collections.singletonList(message);
                logger.error("Critical error while running the auction", exception);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                body = String.format("Critical error while running the auction: %s", message);
            }
        }

        final AmpEvent ampEvent = ampEventBuilder.status(status).errors(errorMessages).build();
        respondWith(context, status, body, startTime, metricRequestStatus, ampEvent);
    }

    private static String originFrom(RoutingContext context) {
        String origin = null;
        final List<String> ampSourceOrigin = context.queryParam("__amp_source_origin");
        if (CollectionUtils.isNotEmpty(ampSourceOrigin)) {
            origin = ampSourceOrigin.get(0);
        }
        if (origin == null) {
            // Just to be safe
            origin = ObjectUtils.defaultIfNull(context.request().headers().get("Origin"), StringUtils.EMPTY);
        }
        return origin;
    }

    private void respondWith(RoutingContext context, int status, String body, long startTime,
                             MetricName metricRequestStatus, AmpEvent event) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
        } else {
            context.response()
                    .exceptionHandler(this::handleResponseException)
                    .setStatusCode(status)
                    .end(body);

            metrics.updateRequestTimeMetric(clock.millis() - startTime);
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, metricRequestStatus);
            analyticsReporter.processEvent(event);
        }
    }

    private void handleResponseException(Throwable exception) {
        logger.warn("Failed to send amp response: {0}", exception.getMessage());
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
    }
}

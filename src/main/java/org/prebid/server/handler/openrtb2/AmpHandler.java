package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.handler.AbstractMeteredHandler;
import org.prebid.server.metric.prebid.RequestHandlerMetrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.response.AmpResponse;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AmpHandler extends AbstractMeteredHandler<RequestHandlerMetrics> {

    private static final Logger logger = LoggerFactory.getLogger(AmpHandler.class);

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>>() {
            };
    private static final TypeReference<ExtBidResponse> EXT_BID_RESPONSE_TYPE_REFERENCE =
            new TypeReference<ExtBidResponse>() {
            };

    private final long defaultTimeout;
    private final AmpRequestFactory ampRequestFactory;
    private final ExchangeService exchangeService;
    private final UidsCookieService uidsCookieService;
    private final Set<String> biddersSupportingCustomTargeting;
    private final BidderCatalog bidderCatalog;

    public AmpHandler(long defaultTimeout, AmpRequestFactory ampRequestFactory, ExchangeService exchangeService,
                      UidsCookieService uidsCookieService, Set<String> biddersSupportingCustomTargeting,
                      BidderCatalog bidderCatalog, RequestHandlerMetrics handlerMetrics, Clock clock,
                      TimeoutFactory timeoutFactory) {
        super(handlerMetrics, clock, timeoutFactory);
        this.defaultTimeout = defaultTimeout;
        this.ampRequestFactory = Objects.requireNonNull(ampRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.biddersSupportingCustomTargeting = Objects.requireNonNull(biddersSupportingCustomTargeting);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public void handle(RoutingContext context) {
        long startTime = getClock().millis();
        getHandlerMetrics().updateRequestMetrics(context, this);

        UidsCookie uidsCookie = getUidsCookie(context);

        ampRequestFactory.fromRequest(context)
                .recover(th -> getHandlerMetrics().updateErrorRequestsMetric(context, this, th))
                .map(bidRequest -> getHandlerMetrics().updateAppAndNoCookieMetrics(context, this, bidRequest,
                        uidsCookie.hasLiveUids(), bidRequest.getApp() != null))
                .compose(bidRequest -> exchangeService.holdAuction(bidRequest, uidsCookie,
                        timeout(startTime, bidRequest)).map(bidResponse -> Tuple2.of(bidRequest, bidResponse)))
                .map((Tuple2<BidRequest, BidResponse> result) -> toAmpResponse(result.getLeft(), result.getRight()))
                .setHandler(responseResult -> handleResult(responseResult, context));
    }

    private Timeout timeout(long startTime, BidRequest bidRequest) {
        return super.timeout(startTime, bidRequest.getTmax(), defaultTimeout);
    }

    private UidsCookie getUidsCookie(RoutingContext context) {
        return uidsCookieService.parseFromRequest(context);
    }

    private AmpResponse toAmpResponse(BidRequest bidRequest, BidResponse bidResponse) {
        // fetch targeting information from response bids
        final List<SeatBid> seatBids = bidResponse.getSeatbid();
        final Map<String, String> targeting = seatBids == null ? Collections.emptyMap() : seatBids.stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream()
                        .filter(Objects::nonNull)
                        .flatMap(bid -> targetingFrom(bid, seatBid.getSeat()).entrySet().stream()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // fetch debug information from response if requested
        final ExtResponseDebug extResponseDebug = Objects.equals(bidRequest.getTest(), 1)
                ? extResponseDebugFrom(bidResponse) : null;

        return AmpResponse.of(targeting, extResponseDebug);
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

    private static void handleResult(AsyncResult<AmpResponse> responseResult, RoutingContext context) {
        addCorsHeaders(context);
        if (responseResult.succeeded()) {
            context.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            context.response().end(Json.encode(responseResult.result()));
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                final List<String> messages = ((InvalidRequestException) exception).getMessages();
                logger.info("Invalid request format: {0}", messages);
                context.response()
                        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .end(messages.stream().map(m -> String.format("Invalid request format: %s", m))
                                .collect(Collectors.joining("\n")));
            } else {
                logger.error("Critical error while running the auction", exception);
                context.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .end(String.format("Critical error while running the auction: %s", exception.getMessage()));
            }
        }
    }

    private static void addCorsHeaders(RoutingContext context) {
        String origin = null;
        final List<String> ampSourceOrigin = context.queryParam("__amp_source_origin");
        if (CollectionUtils.isNotEmpty(ampSourceOrigin)) {
            origin = ampSourceOrigin.iterator().next();
        }
        if (origin == null) {
            // Just to be safe
            origin = ObjectUtils.firstNonNull(context.request().headers().get("Origin"), StringUtils.EMPTY);
        }

        // Add AMP headers
        context.response()
                .putHeader("AMP-Access-Control-Allow-Source-Origin", origin)
                .putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
    }

}

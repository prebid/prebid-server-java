package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.response.AmpResponse;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AmpHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AmpHandler.class);

    private static final Clock CLOCK = Clock.systemDefaultZone();

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ?>>() {
            };

    private final long defaultTimeout;
    private final AmpRequestFactory ampRequestFactory;
    private final ExchangeService exchangeService;
    private final UidsCookieService uidsCookieService;
    private final Metrics metrics;

    public AmpHandler(long defaultTimeout, AmpRequestFactory ampRequestFactory, ExchangeService exchangeService,
                      UidsCookieService uidsCookieService, Metrics metrics) {
        this.defaultTimeout = defaultTimeout;
        this.ampRequestFactory = Objects.requireNonNull(ampRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public void handle(RoutingContext context) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = CLOCK.millis();

        final boolean isSafari = HttpUtil.isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));

        updateRequestMetrics(isSafari);

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);

        ampRequestFactory.fromRequest(context)
                .recover(this::updateErrorRequestsMetric)
                .map(bidRequest -> updateAppAndNoCookieMetrics(bidRequest, uidsCookie.hasLiveUids(), isSafari))
                .compose(bidRequest -> exchangeService.holdAuction(bidRequest, uidsCookie,
                        timeout(bidRequest, startTime)))
                .map(AmpHandler::toAmpResponse)
                .setHandler(responseResult -> handleResult(responseResult, context));
    }

    private GlobalTimeout timeout(BidRequest bidRequest, long startTime) {
        final Long tmax = bidRequest.getTmax();
        return GlobalTimeout.create(startTime, tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }

    private static AmpResponse toAmpResponse(BidResponse bidResponse) {
        if (bidResponse.getSeatbid() == null) {
            return AmpResponse.of(Collections.emptyMap());
        }

        final Map<String, String> targeting = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .flatMap(bid -> bidTargetingFrom(bid).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return AmpResponse.of(targeting);
    }

    private static Map<String, String> bidTargetingFrom(Bid bid) {
        final ExtPrebid<ExtBidPrebid, ?> extBid;
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
                return targeting;
            }
        }

        return Collections.emptyMap();
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
        String originHeader = null;
        final List<String> ampSourceOrigin = context.queryParam("__amp_source_origin");
        if (CollectionUtils.isNotEmpty(ampSourceOrigin)) {
            originHeader = ampSourceOrigin.iterator().next();
        }
        if (originHeader == null) {
            // Just to be safe
            originHeader = context.request().headers().get("Origin");
        }

        // Add AMP headers
        context.response()
                .putHeader("AMP-Access-Control-Allow-Source-Origin", originHeader)
                .putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
    }

    private void updateRequestMetrics(boolean isSafari) {
        metrics.incCounter(MetricName.amp_requests);
        if (isSafari) {
            metrics.incCounter(MetricName.safari_requests);
        }
    }

    private Future<BidRequest> updateErrorRequestsMetric(Throwable failed) {
        metrics.incCounter(MetricName.error_requests);
        return Future.failedFuture(failed);
    }

    private BidRequest updateAppAndNoCookieMetrics(BidRequest bidRequest, boolean isLifeSync, boolean isSafari) {
        if (bidRequest.getApp() != null) {
            metrics.incCounter(MetricName.app_requests);
        } else if (isLifeSync) {
            metrics.incCounter(MetricName.amp_no_cookie);
            if (isSafari) {
                metrics.incCounter(MetricName.safari_no_cookie_requests);
            }
        }

        return bidRequest;
    }
}

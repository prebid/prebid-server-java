package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
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
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AuctionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final long defaultTimeout;
    private final ExchangeService exchangeService;
    private final AuctionRequestFactory auctionRequestFactory;
    private final UidsCookieService uidsCookieService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;
    private final TimeoutFactory timeoutFactory;

    public AuctionHandler(long defaultTimeout, ExchangeService exchangeService,
                          AuctionRequestFactory auctionRequestFactory, UidsCookieService uidsCookieService,
                          AnalyticsReporter analyticsReporter, Metrics metrics, Clock clock,
                          TimeoutFactory timeoutFactory) {
        this.defaultTimeout = defaultTimeout;
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
    }

    @Override
    public void handle(RoutingContext context) {
        final AuctionEvent.AuctionEventBuilder auctionEventBuilder = AuctionEvent.builder();

        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final boolean isSafari = HttpUtil.isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));

        updateRequestMetrics(isSafari);

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        auctionRequestFactory.fromRequest(context)
                .map(bidRequest -> addToEvent(bidRequest, auctionEventBuilder::bidRequest))
                .map(this::updateImpsRequestedMetrics)
                .recover(this::updateImpsRequestedErrorMetrics)
                .map(bidRequest -> updateAppAndNoCookieMetrics(bidRequest, uidsCookie.hasLiveUids(), isSafari))
                .compose(bidRequest ->
                        exchangeService.holdAuction(bidRequest, uidsCookie, timeout(bidRequest, startTime)))
                .recover(this::updateErrorRequestsMetric)
                .map(bidResponse -> addToEvent(bidResponse, auctionEventBuilder::bidResponse))
                .setHandler(responseResult -> handleResult(responseResult, auctionEventBuilder, context));
    }

    private static <T> T addToEvent(T field, Consumer<T> consumer) {
        consumer.accept(field);
        return field;
    }

    private void updateRequestMetrics(boolean isSafari) {
        metrics.incCounter(MetricName.requests);
        metrics.incCounter(MetricName.ortb_requests);
        if (isSafari) {
            metrics.incCounter(MetricName.safari_requests);
        }
    }

    private BidRequest updateImpsRequestedMetrics(BidRequest bidRequest) {
        metrics.incCounter(MetricName.imps_requested, bidRequest.getImp().size());
        return bidRequest;
    }

    private Future<BidRequest> updateImpsRequestedErrorMetrics(Throwable throwable) {
        metrics.incCounter(MetricName.imps_requested, 0L);
        return Future.failedFuture(throwable);
    }

    private BidRequest updateAppAndNoCookieMetrics(BidRequest bidRequest, boolean liveUidsPresent, boolean isSafari) {
        if (bidRequest.getApp() != null) {
            metrics.incCounter(MetricName.app_requests);
        } else if (!liveUidsPresent) {
            metrics.incCounter(MetricName.no_cookie_requests);
            if (isSafari) {
                metrics.incCounter(MetricName.safari_no_cookie_requests);
            }
        }
        return bidRequest;
    }

    private Timeout timeout(BidRequest bidRequest, long startTime) {
        final Long tmax = bidRequest.getTmax();
        return timeoutFactory.create(startTime, tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }

    private Future<BidResponse> updateErrorRequestsMetric(Throwable failed) {
        metrics.incCounter(MetricName.error_requests);
        return Future.failedFuture(failed);
    }

    private void handleResult(AsyncResult<BidResponse> responseResult,
                              AuctionEvent.AuctionEventBuilder auctionEventBuilder, RoutingContext context) {
        final int status;
        final List<String> errorMessages;

        if (responseResult.succeeded()) {
            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .end(Json.encode(responseResult.result()));

            status = HttpResponseStatus.OK.code();
            errorMessages = Collections.emptyList();
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                status = HttpResponseStatus.BAD_REQUEST.code();
                errorMessages = ((InvalidRequestException) exception).getMessages();

                logger.info("Invalid request format: {0}", errorMessages);

                context.response()
                        .setStatusCode(status)
                        .end(errorMessages.stream().map(msg -> String.format("Invalid request format: %s", msg))
                                .collect(Collectors.joining("\n")));
            } else {
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                errorMessages = Collections.singletonList(message);

                context.response()
                        .setStatusCode(status)
                        .end(String.format("Critical error while running the auction: %s", message));
            }
        }

        analyticsReporter.processEvent(auctionEventBuilder.status(status).errors(errorMessages).build());
    }
}

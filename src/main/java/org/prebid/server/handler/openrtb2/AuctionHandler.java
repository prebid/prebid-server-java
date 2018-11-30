package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.model.MetricsContext;
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
    private final long maxTimeout;
    private final ExchangeService exchangeService;
    private final AuctionRequestFactory auctionRequestFactory;
    private final UidsCookieService uidsCookieService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;
    private final TimeoutFactory timeoutFactory;

    public AuctionHandler(long defaultTimeout, long maxTimeout, ExchangeService exchangeService,
                          AuctionRequestFactory auctionRequestFactory, UidsCookieService uidsCookieService,
                          AnalyticsReporter analyticsReporter, Metrics metrics, Clock clock,
                          TimeoutFactory timeoutFactory) {

        if (maxTimeout < defaultTimeout) {
            throw new IllegalArgumentException(
                    String.format("Max timeout cannot be less than default timeout: max=%d, default=%d", maxTimeout,
                            defaultTimeout));
        }

        this.defaultTimeout = defaultTimeout;
        this.maxTimeout = maxTimeout;
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
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final boolean isSafari = HttpUtil.isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));
        metrics.updateSafariRequestsMetric(isSafari);

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);

        final AuctionEvent.AuctionEventBuilder auctionEventBuilder = AuctionEvent.builder()
                .context(context)
                .uidsCookie(uidsCookie);

        auctionRequestFactory.fromRequest(context)
                .map(bidRequest -> addToEvent(bidRequest, auctionEventBuilder::bidRequest, bidRequest))
                .map(bidRequest -> updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest, uidsCookie, isSafari))
                .map(bidRequest -> Tuple2.of(bidRequest, toMetricsContext(bidRequest)))
                .compose((Tuple2<BidRequest, MetricsContext> result) ->
                        exchangeService.holdAuction(result.getLeft(), uidsCookie, timeout(result.getLeft(), startTime),
                                result.getRight(), context)
                                .map(bidResponse -> Tuple2.of(bidResponse, result.getRight())))
                .map((Tuple2<BidResponse, MetricsContext> result) ->
                        addToEvent(result.getLeft(), auctionEventBuilder::bidResponse, result))
                .setHandler(responseResult -> handleResult(responseResult, auctionEventBuilder, context, startTime));
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private BidRequest updateAppAndNoCookieAndImpsRequestedMetrics(BidRequest bidRequest, UidsCookie uidsCookie,
                                                                   boolean isSafari) {
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest.getApp() != null, uidsCookie.hasLiveUids(),
                isSafari, bidRequest.getImp().size());
        return bidRequest;
    }

    private static MetricsContext toMetricsContext(BidRequest bidRequest) {
        return MetricsContext.of(bidRequest.getApp() != null ? MetricName.openrtb2app : MetricName.openrtb2web);
    }

    private Timeout timeout(BidRequest bidRequest, long startTime) {
        final long tmax = ObjectUtils.firstNonNull(bidRequest.getTmax(), 0L);

        final long timeout = tmax <= 0 ? defaultTimeout
                : tmax > maxTimeout ? maxTimeout : tmax;

        return timeoutFactory.create(startTime, timeout);
    }

    private void handleResult(AsyncResult<Tuple2<BidResponse, MetricsContext>> responseResult,
                              AuctionEvent.AuctionEventBuilder auctionEventBuilder, RoutingContext context,
                              long startTime) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped.");
            return;
        }

        final boolean responseSucceeded = responseResult.succeeded();

        final MetricName requestType = responseSucceeded
                ? responseResult.result().getRight().getRequestType()
                : MetricName.openrtb2web;
        final MetricName requestStatus;
        final int status;
        final List<String> errorMessages;

        context.response().exceptionHandler(throwable -> handleResponseException(throwable, requestType));

        if (responseSucceeded) {
            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .end(Json.encode(responseResult.result().getLeft()));

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

        metrics.updateRequestTimeMetric(clock.millis() - startTime);
        metrics.updateRequestTypeMetric(requestType, requestStatus);
        analyticsReporter.processEvent(auctionEventBuilder.status(status).errors(errorMessages).build());
    }

    private void handleResponseException(Throwable throwable, MetricName requestType) {
        logger.warn("Failed to send auction response", throwable);
        metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
    }
}

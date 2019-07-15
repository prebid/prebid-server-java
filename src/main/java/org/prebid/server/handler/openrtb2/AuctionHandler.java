package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.InvalidRequestException;
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

    private final AuctionRequestFactory auctionRequestFactory;
    private final ExchangeService exchangeService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;

    public AuctionHandler(AuctionRequestFactory auctionRequestFactory, ExchangeService exchangeService,
                          AnalyticsReporter analyticsReporter, Metrics metrics, Clock clock) {
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
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

        final AuctionEvent.AuctionEventBuilder auctionEventBuilder = AuctionEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        auctionRequestFactory.fromRequest(routingContext, startTime)
                .map(context -> context.toBuilder()
                        .requestTypeMetric(requestTypeMetric(context.getBidRequest()))
                        .build())

                .map(context -> addToEvent(context.getBidRequest(), auctionEventBuilder::bidRequest, context))
                .map(context -> updateAppAndNoCookieAndImpsRequestedMetrics(context, isSafari))

                .compose(context -> exchangeService.holdAuction(context)
                        .map(bidResponse -> Tuple2.of(bidResponse, context)))

                .map(result -> addToEvent(result.getLeft(), auctionEventBuilder::bidResponse, result))
                .setHandler(result -> handleResult(result, auctionEventBuilder, routingContext, startTime));
    }

    private static MetricName requestTypeMetric(BidRequest bidRequest) {
        return bidRequest.getApp() != null ? MetricName.openrtb2app : MetricName.openrtb2web;
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private AuctionContext updateAppAndNoCookieAndImpsRequestedMetrics(AuctionContext context, boolean isSafari) {
        final BidRequest bidRequest = context.getBidRequest();
        final UidsCookie uidsCookie = context.getUidsCookie();

        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest.getApp() != null, uidsCookie.hasLiveUids(),
                isSafari, bidRequest.getImp().size());

        return context;
    }

    private void handleResult(AsyncResult<Tuple2<BidResponse, AuctionContext>> responseResult,
                              AuctionEvent.AuctionEventBuilder auctionEventBuilder, RoutingContext context,
                              long startTime) {
        final boolean responseSucceeded = responseResult.succeeded();

        final MetricName requestType = responseSucceeded
                ? responseResult.result().getRight().getRequestTypeMetric()
                : MetricName.openrtb2web;

        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
            return;
        }

        context.response().exceptionHandler(throwable -> handleResponseException(throwable, requestType));

        final MetricName requestStatus;
        final int status;
        final List<String> errorMessages;

        if (responseSucceeded) {
            context.response()
                    .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
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

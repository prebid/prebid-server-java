package org.prebid.server.handler.openrtb2;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.VideoRequestFactory;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.response.VideoResponse;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class VideoHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VideoHandler.class);

    private static final MetricName REQUEST_TYPE_METRIC = MetricName.video;

    private final VideoRequestFactory videoRequestFactory;
    private final VideoResponseFactory videoResponseFactory;
    private final ExchangeService exchangeService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;
    private final JacksonMapper mapper;

    public VideoHandler(VideoRequestFactory videoRequestFactory, VideoResponseFactory videoResponseFactory,
                        ExchangeService exchangeService, AnalyticsReporter analyticsReporter, Metrics metrics,
                        Clock clock, JacksonMapper mapper) {
        this.videoRequestFactory = Objects.requireNonNull(videoRequestFactory);
        this.videoResponseFactory = Objects.requireNonNull(videoResponseFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
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
        final VideoEvent.VideoEventBuilder videoEventBuilder = VideoEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        videoRequestFactory.fromRequest(routingContext, startTime)
                .map(contextToErrors -> updateAuctionContextWithPodErrors(contextToErrors, videoEventBuilder))

                .compose(contextToErrors -> exchangeService.holdAuction(contextToErrors.getData())
                        .map(bidResponse -> Tuple2.of(bidResponse, contextToErrors)))

                .map(result -> videoResponseFactory.toVideoResponse(result.getRight().getData().getBidRequest(),
                        result.getLeft(), result.getRight().getPodErrors()))

                .map(videoResponse -> addToEvent(videoResponse, videoEventBuilder::bidResponse, videoResponse))
                .setHandler(responseResult -> handleResult(responseResult, videoEventBuilder, routingContext,
                        startTime));
    }

    private WithPodErrors<AuctionContext> updateAuctionContextWithPodErrors(
            WithPodErrors<AuctionContext> contextToErrors, VideoEvent.VideoEventBuilder eventBuilder) {

        final AuctionContext typeMetricAuctionContext = contextToErrors.getData().toBuilder()
                .requestTypeMetric(REQUEST_TYPE_METRIC)
                .build();

        addToEvent(typeMetricAuctionContext, eventBuilder::auctionContext, typeMetricAuctionContext);

        return WithPodErrors.of(typeMetricAuctionContext, contextToErrors.getPodErrors());

    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private void handleResult(AsyncResult<VideoResponse> responseResult, VideoEvent.VideoEventBuilder videoEventBuilder,
                              RoutingContext context, long startTime) {
        final boolean responseSucceeded = responseResult.succeeded();
        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final int status;
        final String body;

        if (responseSucceeded) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();

            status = HttpResponseStatus.OK.code();
            context.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
            body = mapper.encode(responseResult.result());
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;
                errorMessages = ((InvalidRequestException) exception).getMessages();
                logger.info("Invalid request format: {0}", errorMessages);

                status = HttpResponseStatus.BAD_REQUEST.code();
                body = errorMessages.stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.joining("\n"));
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String errorMessage = exception.getMessage();
                logger.info("Unauthorized: {0}", errorMessage);

                errorMessages = Collections.singletonList(errorMessage);

                status = HttpResponseStatus.UNAUTHORIZED.code();
                body = String.format("Unauthorised: %s", errorMessage);
            } else {
                metricRequestStatus = MetricName.err;
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                body = String.format("Critical error while running the auction: %s", message);
            }
        }
        final VideoEvent videoEvent = videoEventBuilder.status(status).errors(errorMessages).build();
        respondWith(context, status, body, startTime, metricRequestStatus, videoEvent);
    }

    private void respondWith(RoutingContext context, int status, String body, long startTime,
                             MetricName metricRequestStatus, VideoEvent event) {
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

    private void handleResponseException(Throwable throwable) {
        logger.warn("Failed to send video response: {0}", throwable.getMessage());
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
    }
}

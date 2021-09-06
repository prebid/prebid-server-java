package org.prebid.server.handler.openrtb2;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.requestfactory.VideoRequestFactory;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;
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
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final Clock clock;
    private final JacksonMapper mapper;

    public VideoHandler(VideoRequestFactory videoRequestFactory, VideoResponseFactory videoResponseFactory,
                        ExchangeService exchangeService, AnalyticsReporterDelegator analyticsDelegator, Metrics metrics,
                        Clock clock, JacksonMapper mapper) {
        this.videoRequestFactory = Objects.requireNonNull(videoRequestFactory);
        this.videoResponseFactory = Objects.requireNonNull(videoResponseFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
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

        final VideoEvent.VideoEventBuilder videoEventBuilder = VideoEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        videoRequestFactory.fromRequest(routingContext, startTime)
                .map(contextToErrors -> addToEvent(
                        contextToErrors.getData(), videoEventBuilder::auctionContext, contextToErrors))

                .compose(contextToErrors -> exchangeService.holdAuction(contextToErrors.getData())
                        .map(bidResponse -> Tuple2.of(bidResponse, contextToErrors)))

                .map(result -> videoResponseFactory.toVideoResponse(result.getRight().getData(),
                        result.getLeft(), result.getRight().getPodErrors()))

                .map(videoResponse -> addToEvent(videoResponse, videoEventBuilder::bidResponse, videoResponse))
                .setHandler(responseResult -> handleResult(responseResult, videoEventBuilder, routingContext,
                        startTime));
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private void handleResult(AsyncResult<VideoResponse> responseResult, VideoEvent.VideoEventBuilder videoEventBuilder,
                              RoutingContext routingContext, long startTime) {
        final boolean responseSucceeded = responseResult.succeeded();
        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final HttpResponseStatus status;
        final String body;

        if (responseSucceeded) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();

            status = HttpResponseStatus.OK;
            routingContext.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
            body = mapper.encode(responseResult.result());
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;
                errorMessages = ((InvalidRequestException) exception).getMessages();
                logger.info("Invalid request format: {0}", errorMessages);

                status = HttpResponseStatus.BAD_REQUEST;
                body = errorMessages.stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.joining("\n"));
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String errorMessage = exception.getMessage();
                logger.info("Unauthorized: {0}", errorMessage);
                errorMessages = Collections.singletonList(errorMessage);

                status = HttpResponseStatus.UNAUTHORIZED;
                body = String.format("Unauthorised: %s", errorMessage);
            } else {
                metricRequestStatus = MetricName.err;
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                body = String.format("Critical error while running the auction: %s", message);
            }
        }

        final VideoEvent videoEvent = videoEventBuilder.status(status.code()).errors(errorMessages).build();
        final AuctionContext auctionContext = videoEvent.getAuctionContext();
        final PrivacyContext privacyContext = auctionContext != null ? auctionContext.getPrivacyContext() : null;
        final TcfContext tcfContext = privacyContext != null ? privacyContext.getTcfContext() : TcfContext.empty();
        respondWith(routingContext, status, body, startTime, metricRequestStatus, videoEvent, tcfContext);
    }

    private void respondWith(RoutingContext routingContext,
                             HttpResponseStatus status,
                             String body,
                             long startTime,
                             MetricName metricRequestStatus,
                             VideoEvent event,
                             TcfContext tcfContext) {

        final boolean responseSent = HttpUtil.executeSafely(routingContext, Endpoint.openrtb2_video,
                response -> response
                        .exceptionHandler(this::handleResponseException)
                        .setStatusCode(status.code())
                        .end(body));

        if (responseSent) {
            metrics.updateRequestTimeMetric(REQUEST_TYPE_METRIC, clock.millis() - startTime);
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, metricRequestStatus);
            analyticsDelegator.processEvent(event, tcfContext);
        } else {
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
        }
    }

    private void handleResponseException(Throwable throwable) {
        logger.warn("Failed to send video response: {0}", throwable.getMessage());
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
    }
}

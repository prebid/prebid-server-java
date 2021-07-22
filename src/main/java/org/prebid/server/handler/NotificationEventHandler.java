package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.events.EventRequest;
import org.prebid.server.events.EventUtil;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * Accepts notifications from browsers and mobile application for further processing by {@link AnalyticsReporter}
 * and responding with tracking pixel when requested.
 */
public class NotificationEventHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEventHandler.class);

    private static final String TRACKING_PIXEL_PNG = "static/tracking-pixel.png";
    private static final String PNG_CONTENT_TYPE = "image/png";

    private static final long DEFAULT_TIMEOUT = 1000L;

    private final AnalyticsReporterDelegator analyticsDelegator;
    private final TimeoutFactory timeoutFactory;
    private final ApplicationSettings applicationSettings;
    private final TrackingPixel trackingPixel;

    public NotificationEventHandler(AnalyticsReporterDelegator analyticsDelegator,
                                    TimeoutFactory timeoutFactory,
                                    ApplicationSettings applicationSettings) {

        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);

        trackingPixel = createTrackingPixel();
    }

    private static TrackingPixel createTrackingPixel() {
        final byte[] bytes;
        try {
            bytes = ResourceUtil.readByteArrayFromClassPath(TRACKING_PIXEL_PNG);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Failed to load pixel image at %s", TRACKING_PIXEL_PNG), e);
        }
        return TrackingPixel.of(PNG_CONTENT_TYPE, bytes);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            EventUtil.validateType(routingContext);
            EventUtil.validateBidId(routingContext);
            EventUtil.validateTimestamp(routingContext);
            EventUtil.validateFormat(routingContext);
            EventUtil.validateAnalytics(routingContext);
            EventUtil.validateIntegration(routingContext);
        } catch (IllegalArgumentException e) {
            respondWithBadRequest(routingContext, e.getMessage());
            return;
        }

        try {
            EventUtil.validateAccountId(routingContext);
        } catch (IllegalArgumentException e) {
            respondWithUnauthorized(routingContext, e.getMessage());
            return;
        }

        final EventRequest eventRequest = EventUtil.from(routingContext);
        if (eventRequest.getAnalytics() == EventRequest.Analytics.enabled) {
            getAccountById(eventRequest.getAccountId())
                    .setHandler(async -> handleEvent(async, eventRequest, routingContext));
        } else {
            respondWithOk(routingContext, eventRequest.getFormat() == EventRequest.Format.image);
        }
    }

    /**
     * Returns {@link Account} fetched by {@link ApplicationSettings}.
     */
    private Future<Account> getAccountById(String accountId) {
        return applicationSettings.getAccountById(accountId, timeoutFactory.create(DEFAULT_TIMEOUT))
                .recover(exception -> handleAccountExceptionOrFallback(exception, accountId));
    }

    /**
     * Returns fallback {@link Account} if account not found or propagate error if fetching failed.
     */
    private static Future<Account> handleAccountExceptionOrFallback(Throwable exception, String accountId) {
        if (exception instanceof PreBidException) {
            return Future.succeededFuture(Account.builder().id(accountId).eventsEnabled(false).build());
        }
        return Future.failedFuture(exception);
    }

    private void handleEvent(AsyncResult<Account> async, EventRequest eventRequest, RoutingContext routingContext) {
        if (async.failed()) {
            respondWithServerError(routingContext, "Error occurred while fetching account", async.cause());
        } else {
            final Account account = async.result();

            if (Objects.equals(account.getEventsEnabled(), true)) {
                final NotificationEvent notificationEvent = NotificationEvent.builder()
                        .type(eventRequest.getType() == EventRequest.Type.win
                                ? NotificationEvent.Type.win : NotificationEvent.Type.imp)
                        .bidId(eventRequest.getBidId())
                        .account(account)
                        .bidder(eventRequest.getBidder())
                        .timestamp(eventRequest.getTimestamp())
                        .integration(eventRequest.getIntegration())
                        .httpContext(HttpContext.from(routingContext))
                        .build();
                analyticsDelegator.processEvent(notificationEvent);

                respondWithOk(routingContext, eventRequest.getFormat() == EventRequest.Format.image);
            } else {
                respondWithUnauthorized(routingContext,
                        String.format("Account '%s' doesn't support events", account.getId()));
            }
        }
    }

    private void respondWithOk(RoutingContext routingContext, boolean respondWithPixel) {
        if (respondWithPixel) {
            HttpUtil.executeSafely(routingContext, Endpoint.event,
                    response -> response
                            .putHeader(HttpHeaders.CONTENT_TYPE, trackingPixel.getContentType())
                            .end(Buffer.buffer(trackingPixel.getContent())));
        } else {
            HttpUtil.executeSafely(routingContext, Endpoint.event,
                    HttpServerResponse::end);
        }
    }

    private static void respondWithBadRequest(RoutingContext routingContext, String message) {
        respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, message);
    }

    private static void respondWithUnauthorized(RoutingContext routingContext, String message) {
        respondWith(routingContext, HttpResponseStatus.UNAUTHORIZED, message);
    }

    private static void respondWithServerError(RoutingContext routingContext, String message, Throwable exception) {
        logger.warn(message, exception);
        final String body = String.format("%s: %s", message, exception.getMessage());
        respondWith(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR, body);
    }

    private static void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body) {
        HttpUtil.executeSafely(routingContext, Endpoint.event,
                response -> response
                        .setStatusCode(status.code())
                        .end(body));
    }

    /**
     * Internal class for holding pixels content type to its value.
     */
    @AllArgsConstructor(staticName = "of")
    @Value
    private static class TrackingPixel {

        String contentType;

        byte[] content;
    }
}

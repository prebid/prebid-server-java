package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.events.EventRequest;
import org.prebid.server.events.EventUtil;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
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

    private final AnalyticsReporter analyticsReporter;
    private final TimeoutFactory timeoutFactory;
    private final ApplicationSettings applicationSettings;
    private final TrackingPixel trackingPixel;

    public NotificationEventHandler(AnalyticsReporter analyticsReporter, TimeoutFactory timeoutFactory,
                                    ApplicationSettings applicationSettings) {
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
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
    public void handle(RoutingContext context) {
        try {
            EventUtil.validateType(context);
            EventUtil.validateBidId(context);
            EventUtil.validateFormat(context);
            EventUtil.validateAnalytics(context);
        } catch (IllegalArgumentException e) {
            respondWithBadStatus(context, e.getMessage());
            return;
        }

        try {
            EventUtil.validateAccountId(context);
        } catch (IllegalArgumentException e) {
            respondWithUnauthorized(context, e.getMessage());
            return;
        }

        final EventRequest eventRequest = EventUtil.from(context);
        if (eventRequest.getAnalytics() == EventRequest.Analytics.enabled) {
            getAccountById(eventRequest.getAccountId())
                    .setHandler(async -> handleEvent(async, eventRequest, context));
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
        logger.warn("Error occurred while fetching account", exception);
        return Future.failedFuture(exception);
    }

    private void handleEvent(AsyncResult<Account> async, EventRequest eventRequest, RoutingContext context) {
        if (async.failed()) {
            respondWithServerError(context, async.cause());
        } else {
            final Account account = async.result();

            if (Objects.equals(account.getEventsEnabled(), true)) {
                final NotificationEvent notificationEvent = NotificationEvent.builder()
                        .type(eventRequest.getType() == EventRequest.Type.win
                                ? NotificationEvent.Type.win : NotificationEvent.Type.imp)
                        .bidId(eventRequest.getBidId())
                        .account(account)
                        .httpContext(HttpContext.from(context))
                        .build();
                analyticsReporter.processEvent(notificationEvent);

                respondWithOkStatus(context, eventRequest.getFormat() == EventRequest.Format.image);
            } else {
                respondWithUnauthorized(context, String.format("Account '%s' doesn't support events", account.getId()));
            }
        }
    }

    private void respondWithOkStatus(RoutingContext context, boolean respondWithPixel) {
        if (respondWithPixel) {
            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, trackingPixel.getContentType())
                    .end(Buffer.buffer(trackingPixel.getContent()));
        } else {
            context.response().end();
        }
    }

    private static void respondWithBadStatus(RoutingContext context, String message) {
        respondWithError(context, HttpResponseStatus.BAD_REQUEST, message);
    }

    private static void respondWithUnauthorized(RoutingContext context, String message) {
        respondWithError(context, HttpResponseStatus.UNAUTHORIZED, message);
    }

    private static void respondWithServerError(RoutingContext context, Throwable exception) {
        final String message = "Error occurred while fetching account";
        logger.warn(message, exception);
        respondWithError(context, HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
    }

    private static void respondWithError(RoutingContext context, HttpResponseStatus status, String message) {
        context.response().setStatusCode(status.code()).end(message);
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

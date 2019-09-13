package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.exception.InvalidRequestException;
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

    private static final String TYPE_PARAMETER = "t";
    private static final String WIN_TYPE = "win";
    private static final String IMP_TYPE = "imp";

    private static final String BID_ID_PARAMETER = "b";
    private static final String ACCOUNT_PARAMETER = "a";

    private static final String FORMAT_PARAMETER = "f";
    private static final String IMAGE_FORMAT = "i";
    private static final String BLANK_FORMAT = "b";

    private static final String ANALYTICS_PARAMETER = "x";
    private static final String ENABLED_ANALYTICS = "1";
    private static final String DISABLED_ANALYTICS = "0";

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
        final HttpServerResponse response = context.response();
        final MultiMap queryParameters = context.request().params();
        try {
            validateParametersForBadRequestError(queryParameters);
        } catch (InvalidRequestException e) {
            respondWithBadStatus(response, e.getMessage());
            return;
        }

        try {
            validateParametersForUnauthorizedError(queryParameters);
        } catch (InvalidRequestException e) {
            respondWithUnauthorized(response, e.getMessage());
            return;
        }

        final String analytics = queryParameters.get(ANALYTICS_PARAMETER);
        final boolean isAnalyticsRequested = analytics == null || analytics.equals(ENABLED_ANALYTICS);

        if (isAnalyticsRequested) {
            final String accountId = queryParameters.get(ACCOUNT_PARAMETER);

            getAccountById(accountId)
                    .setHandler(async -> handleEvent(async, context));
        }
    }

    private static void validateParametersForBadRequestError(MultiMap queryParameters) {
        final String type = queryParameters.get(TYPE_PARAMETER);
        if (ObjectUtils.notEqual(type, IMP_TYPE) && ObjectUtils.notEqual(type, WIN_TYPE)) {
            throw new InvalidRequestException(
                    String.format("Type '%s' is required query parameter. Possible values are %s and %s, but was %s",
                            TYPE_PARAMETER, WIN_TYPE, IMP_TYPE, type));
        }

        final String bidId = queryParameters.get(BID_ID_PARAMETER);
        if (StringUtils.isBlank(bidId)) {
            throw new InvalidRequestException(
                    String.format("BidId '%s' is required query parameter and can't be empty", BID_ID_PARAMETER));
        }

        final String format = queryParameters.get(FORMAT_PARAMETER);
        if (StringUtils.isNotBlank(format) && ObjectUtils.notEqual(format, BLANK_FORMAT)
                && ObjectUtils.notEqual(format, IMAGE_FORMAT)) {
            throw new InvalidRequestException(
                    String.format("Format '%s' query parameter is invalid. Possible values are %s and %s, but was %s",
                            FORMAT_PARAMETER, BLANK_FORMAT, IMAGE_FORMAT, format));
        }

        final String analytics = queryParameters.get(ANALYTICS_PARAMETER);
        if (StringUtils.isNotBlank(analytics) && ObjectUtils.notEqual(analytics, ENABLED_ANALYTICS)
                && ObjectUtils.notEqual(analytics, DISABLED_ANALYTICS)) {
            throw new InvalidRequestException(
                    String.format(
                            "Analytics '%s' query parameter is invalid. Possible values are %s and %s, but was %s",
                            ANALYTICS_PARAMETER, ENABLED_ANALYTICS, DISABLED_ANALYTICS, analytics));
        }
    }

    private static void validateParametersForUnauthorizedError(MultiMap queryParameters) {
        final String account = queryParameters.get(ACCOUNT_PARAMETER);
        if (StringUtils.isBlank(account)) {
            throw new InvalidRequestException(
                    String.format("Account '%s' is required query parameter and can't be empty", ACCOUNT_PARAMETER));
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

    private void handleEvent(AsyncResult<Account> async, RoutingContext context) {
        final HttpServerResponse response = context.response();

        if (async.failed()) {
            respondWithServerError(response, async.cause());
        } else {
            final Account account = async.result();

            if (Objects.equals(account.getEventsEnabled(), true)) {
                analyticsReporter.processEvent(makeNotificationEvent(account, context));
                respondWithOkStatus(response, context.request().params().get(FORMAT_PARAMETER));
            } else {
                respondWithUnauthorized(response,
                        String.format("Account '%s' doesn't not support events", account.getId()));
            }
        }
    }

    private static NotificationEvent makeNotificationEvent(Account account, RoutingContext context) {
        final MultiMap queryParameters = context.request().params();

        return NotificationEvent.builder()
                .type(toNotificationType(queryParameters.get(TYPE_PARAMETER)))
                .bidId(queryParameters.get(BID_ID_PARAMETER))
                .account(account)
                .httpContext(HttpContext.from(context))
                .build();
    }

    private static NotificationEvent.Type toNotificationType(String type) {
        return Objects.equals(WIN_TYPE, type) ? NotificationEvent.Type.win : NotificationEvent.Type.imp;
    }

    private void respondWithOkStatus(HttpServerResponse response, String format) {
        if (Objects.equals(format, IMAGE_FORMAT)) {
            response.putHeader(HttpHeaders.CONTENT_TYPE, trackingPixel.getContentType())
                    .end(Buffer.buffer(trackingPixel.getContent()));
        } else {
            response.end();
        }
    }

    private static void respondWithBadStatus(HttpServerResponse response, String message) {
        respondWithError(response, message, HttpResponseStatus.BAD_REQUEST);
    }

    private static void respondWithUnauthorized(HttpServerResponse response, String message) {
        respondWithError(response, message, HttpResponseStatus.UNAUTHORIZED);
    }

    private static void respondWithServerError(HttpServerResponse response, Throwable exception) {
        final String message = "Error occurred while fetching account";
        logger.warn(message, exception);
        respondWithError(response, message, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private static void respondWithError(HttpServerResponse response, String message, HttpResponseStatus status) {
        response.setStatusCode(status.code()).end(message);
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

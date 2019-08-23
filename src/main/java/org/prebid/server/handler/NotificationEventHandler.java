package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.exception.InvalidRequestException;
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

    private static final Long DEFAULT_TIMEOUT = 1000L;
    private static final String TRACKING_PIXEL_PNG = "static/tracking-pixel.png";
    private static final String PNG_CONTENT_TYPE = "image/png";

    private static final String TYPE_PARAMETER = "t";
    private static final String WIN_TYPE = "w";
    private static final String IMP_TYPE = "i";

    private static final String BID_ID_PARAMETER = "b";
    private static final String ACCOUNT_PARAMETER = "a";

    private static final String FORMAT_PARAMETER = "f";
    private static final String PIXEL_FORMAT = "i";
    private static final String BLANK_FORMAT = "b";

    private static final String ANALYTICS_PARAMETER = "x";
    private static final String ENABLED_ANALYTICS = "1";
    private static final String DISABLED_ANALYTICS = "0";

    private final AnalyticsReporter analyticsReporter;
    private final TrackingPixel trackingPixel;
    private TimeoutFactory timeoutFactory;
    private ApplicationSettings applicationSettings;

    public NotificationEventHandler(AnalyticsReporter analyticsReporter, TimeoutFactory timeoutFactory,
                                    ApplicationSettings applicationSettings) {
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.trackingPixel = TrackingPixel.of(PNG_CONTENT_TYPE, readTrackingPixel(TRACKING_PIXEL_PNG));
    }

    private static byte[] readTrackingPixel(String path) {
        try {
            return ResourceUtil.readByteArrayFromClassPath(path);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Failed to load pixel image at %s", path), e);
        }
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerResponse response = context.response();
        final MultiMap queryParameters = context.request().params();
        try {
            validateParameters(queryParameters, response);
        } catch (InvalidRequestException ex) {
            return;
        }

        final String format = queryParameters.get(FORMAT_PARAMETER);
        final String analytics = queryParameters.get(ANALYTICS_PARAMETER);
        final boolean isAnalyticsEnabled = analytics == null || analytics.equals(ENABLED_ANALYTICS);

        final String accountId = queryParameters.get(ACCOUNT_PARAMETER);
        isAccountEventEnabled(accountId)
                .setHandler(isEnabledResult -> handleEvent(isEnabledResult, isAnalyticsEnabled, format, context,
                        response));
    }

    private void handleEvent(AsyncResult<Boolean> isEnabledResult, boolean isAnalyticsEnabled, String format,
                                    RoutingContext context, HttpServerResponse response) {
        if (isEnabledResult.result()) {
            if (isAnalyticsEnabled) {
                analyticsReporter.processEvent(makeNotificationEvent(context));
                respondWithOkStatus(response, format);
            }
        } else {
            respondWithUnauthorized(response, "Given 'accountId' is not supporting the event");
        }
    }

    private Future<Boolean> isAccountEventEnabled(String accountId) {
        return applicationSettings.getAccountById(accountId, timeoutFactory.create(DEFAULT_TIMEOUT))
                .map(Account::getEventsEnabled)
                .otherwise(false);
    }

    private static void validateParameters(MultiMap queryParameters, HttpServerResponse response) {
        final String type = queryParameters.get(TYPE_PARAMETER);
        if (ObjectUtils.notEqual(type, IMP_TYPE) && ObjectUtils.notEqual(type, WIN_TYPE)) {
            final String typeErrorMessage = String.format("'type' is required query parameter. Possible values are %s "
                    + "and %s, but was %s", IMP_TYPE, WIN_TYPE, type);
            respondWithBadStatus(response, typeErrorMessage);
            throw new InvalidRequestException(typeErrorMessage);
        }

        final String bidId = queryParameters.get(BID_ID_PARAMETER);
        if (StringUtils.isBlank(bidId)) {
            final String bidIdErrorMessage = "'bidid' is required query parameter and can't be empty";
            respondWithBadStatus(response, bidIdErrorMessage);
            throw new InvalidRequestException(bidIdErrorMessage);
        }

        final String account = queryParameters.get(ACCOUNT_PARAMETER);
        if (StringUtils.isBlank(account)) {
            final String accountErrorMessage = "'account' is required query parameter and can't be empty";
            respondWithUnauthorized(response, accountErrorMessage);
            throw new InvalidRequestException(accountErrorMessage);
        }

        final String format = queryParameters.get(FORMAT_PARAMETER);
        if (StringUtils.isNotBlank(format) && ObjectUtils.notEqual(format, BLANK_FORMAT)
                && ObjectUtils.notEqual(format, PIXEL_FORMAT)) {
            final String formatErrorMessage = String.format("'format' is required query parameter. Possible values "
                    + "are %s and %s, but was %s", BLANK_FORMAT, PIXEL_FORMAT, format);
            respondWithBadStatus(response, formatErrorMessage);
            throw new InvalidRequestException(formatErrorMessage);
        }

        final String analytics = queryParameters.get(ANALYTICS_PARAMETER);
        if (StringUtils.isNotBlank(analytics) && ObjectUtils.notEqual(analytics, ENABLED_ANALYTICS)
                && ObjectUtils.notEqual(analytics, DISABLED_ANALYTICS)) {
            final String analyticsErrorMessage = String.format("'analytics' is required query parameter. Possible "
                    + "values are %s and %s, but was %s", ENABLED_ANALYTICS, DISABLED_ANALYTICS, analytics);
            respondWithBadStatus(response, analyticsErrorMessage);
            throw new InvalidRequestException(analyticsErrorMessage);
        }
    }

    private static NotificationEvent makeNotificationEvent(RoutingContext context) {
        final MultiMap queryParameters = context.request().params();
        final String type = queryParameters.get(TYPE_PARAMETER);
        final String bidId = queryParameters.get(BID_ID_PARAMETER);
        final String accountId = queryParameters.get(ACCOUNT_PARAMETER);
        final HttpContext httpContext = HttpContext.from(context);
        return NotificationEvent.of(type, bidId, accountId, httpContext);
    }

    private void respondWithOkStatus(HttpServerResponse response, String format) {
        if (StringUtils.equals(format, IMP_TYPE)) {
            response.putHeader(HttpHeaders.CONTENT_TYPE, trackingPixel.getContentType())
                    .end(Buffer.buffer(trackingPixel.getContent()));
        } else {
            response.end();
        }
    }

    private static void respondWithUnauthorized(HttpServerResponse response, String message) {
        respondWithError(response, message, HttpResponseStatus.UNAUTHORIZED);
    }

    private static void respondWithBadStatus(HttpServerResponse response, String message) {
        respondWithError(response, message, HttpResponseStatus.BAD_REQUEST);
    }

    private static void respondWithError(HttpServerResponse response, String message, HttpResponseStatus status) {
        response.setStatusCode(status.code()).end(message);
    }

    /**
     * Internal class for holding pixels content type to its value
     */
    @AllArgsConstructor(staticName = "of")
    @Value
    private static class TrackingPixel {
        String contentType;
        byte[] content;
    }
}


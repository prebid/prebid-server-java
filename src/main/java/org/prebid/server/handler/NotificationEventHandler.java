package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Accepts notifications from browsers and mobile application for further processing by {@link AnalyticsReporter}
 * and responding with tracking pixel when requested.
 */
public class NotificationEventHandler implements Handler<RoutingContext> {

    private static final String TRACKING_PIXEL_PNG = "static/tracking-pixel.png";
    private static final String TRACKING_PIXEL_JPG = "static/tracking-pixel.jpg";
    private static final String VIEW_TYPE = "view";
    private static final String WIN_TYPE = "win";
    private static final String FORMAT_PARAMETER = "format";
    private static final String TYPE_PARAMETER = "type";
    private static final String BID_ID_PARAMETER = "bidid";
    private static final String BIDDER_PARAMETER = "bidder";
    private static final String JPG_FORMAT = "jpg";
    private static final String PNG_FORMAT = "png";
    private static final String JPG_CONTENT_TYPE = "image/jpeg";
    private static final String PNG_CONTENT_TYPE = "image/png";

    private final AnalyticsReporter analyticsReporter;
    private final Map<String, TrackingPixel> trackingPixels;

    private NotificationEventHandler(AnalyticsReporter analyticsReporter, Map<String, TrackingPixel> trackingPixels) {
        this.analyticsReporter = analyticsReporter;
        this.trackingPixels = trackingPixels;
    }

    public static NotificationEventHandler create(AnalyticsReporter analyticsReporter) {
        final Map<String, TrackingPixel> trackingPixels = new HashMap<>();
        trackingPixels.put(JPG_FORMAT, TrackingPixel.of(JPG_CONTENT_TYPE, readTrackingPixel(TRACKING_PIXEL_JPG)));
        trackingPixels.put(PNG_FORMAT, TrackingPixel.of(PNG_CONTENT_TYPE, readTrackingPixel(TRACKING_PIXEL_PNG)));
        return new NotificationEventHandler(Objects.requireNonNull(analyticsReporter), trackingPixels);
    }

    @Override
    public void handle(RoutingContext context) {
        final MultiMap queryParameters = context.request().params();

        final NotificationEvent notificationEvent;
        try {
            notificationEvent = makeNotificationEvent(queryParameters);
        } catch (InvalidRequestException ex) {
            respondWithBadStatus(context, ex.getMessage());
            return;
        }

        analyticsReporter.processEvent(notificationEvent);

        final String format = queryParameters.get(FORMAT_PARAMETER);
        try {
            validateEventRequestQueryParams(format);
        } catch (InvalidRequestException ex) {
            respondWithBadStatus(context, ex.getMessage());
            return;
        }

        respondWithOkStatus(context, format);
    }

    private static byte[] readTrackingPixel(String path) {
        try {
            return ResourceUtil.readByteArrayFromClassPath(path);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Failed to load pixel image at %s", path), e);
        }
    }

    private NotificationEvent makeNotificationEvent(MultiMap queryParameters) {
        final String type = queryParameters.get(TYPE_PARAMETER);
        if (ObjectUtils.notEqual(type, VIEW_TYPE) && ObjectUtils.notEqual(type, WIN_TYPE)) {
            throw new InvalidRequestException(String.format(
                    "Type is required query parameter. Possible values are win and view, but was %s", type));
        }

        final String bidId = queryParameters.get(BID_ID_PARAMETER);
        if (StringUtils.isBlank(bidId)) {
            throw new InvalidRequestException("bidid is required query parameter and can't be empty.");
        }

        final String bidder = queryParameters.get(BIDDER_PARAMETER);
        if (StringUtils.isBlank(bidder)) {
            throw new InvalidRequestException("bidder is required query parameter and can't be empty.");
        }
        return NotificationEvent.of(type, bidId, bidder);
    }

    private void validateEventRequestQueryParams(String format) {
        if (format != null && !trackingPixels.containsKey(format)) {
            throw new InvalidRequestException(String.format(
                    "'format' query parameter can have one of the next values: %s", trackingPixels.keySet()));
        }
    }

    private void respondWithBadStatus(RoutingContext context, String message) {
        context.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                .end(String.format("Request is invalid: %s", message));
    }

    private void respondWithOkStatus(RoutingContext context, String format) {
        if (format != null) {
            final TrackingPixel trackingPixel = trackingPixels.get(format);
            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, trackingPixel.getContentType())
                    .end(Buffer.buffer(trackingPixel.getContent()));
        } else {
            context.response().end();
        }
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

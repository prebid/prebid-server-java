package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.request.EventNotificationRequest;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
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
    private static final String JPG = "jpg";
    private static final String PNG = "png";
    private static final String JPG_HEADER = "image/jpeg";
    private static final String PNG_HEADER = "image/png";

    private final AnalyticsReporter analyticsReporter;
    private final byte[] trackingPixelPng;
    private final byte[] trackingPixelJpg;

    private NotificationEventHandler(AnalyticsReporter analyticsReporter, byte[] trackingPixelPng,
                                     byte[] trackingPixelJpg) {
        this.analyticsReporter = analyticsReporter;
        this.trackingPixelJpg = trackingPixelJpg;
        this.trackingPixelPng = trackingPixelPng;
    }

    public static NotificationEventHandler create(AnalyticsReporter analyticsReporter) {
        return new NotificationEventHandler(Objects.requireNonNull(analyticsReporter),
                readTrackingPixel(TRACKING_PIXEL_PNG), readTrackingPixel(TRACKING_PIXEL_JPG));
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
        final EventNotificationRequest eventNotificationRequest;
        try {
            eventNotificationRequest = makeEventRequest(context.getBody());
        } catch (InvalidRequestException ex) {
            respondWithBadStatus(context, ex.getMessage());
            return;
        }

        final NotificationEvent notificationEvent = new NotificationEvent(eventNotificationRequest.getType(),
                eventNotificationRequest.getBidId(), eventNotificationRequest.getBidder());

        analyticsReporter.processEvent(notificationEvent);

        final Map<String, String> queryParams = HttpContext.from(context).getQueryParams();
        final String format = queryParams.get(FORMAT_PARAMETER);

        try {
            validateEventRequestQueryParams(format);
        } catch (InvalidRequestException ex) {
            respondWithBadStatus(context, ex.getMessage());
            return;
        }

        respondWithOkStatus(context, format);
    }

    private EventNotificationRequest makeEventRequest(Buffer body) {
        if (body == null) {
            throw new InvalidRequestException(
                    "Request body was empty. Expected request with body has next fields: type, bidid and bidder.");
        }

        final EventNotificationRequest eventNotificationRequest;
        try {
            eventNotificationRequest = Json.decodeValue(body, EventNotificationRequest.class);
        } catch (DecodeException e) {
            throw new InvalidRequestException(
                    "Request body couldn't be parsed. Expected request body has next fields: type, bidid and bidder.");
        }

        validateEventRequestBody(eventNotificationRequest);
        return eventNotificationRequest;
    }

    private void validateEventRequestBody(EventNotificationRequest eventNotificationRequest) {
        final String type = eventNotificationRequest.getType();
        if (ObjectUtils.notEqual(type, VIEW_TYPE) && ObjectUtils.notEqual(type, WIN_TYPE)) {
            throw new InvalidRequestException(String.format(
                    "Type is required parameter. Possible values are win and view, but was %s", type));
        }

        final String bidId = eventNotificationRequest.getBidId();
        if (StringUtils.isBlank(bidId)) {
            throw new InvalidRequestException("bidid is required and can't be empty.");
        }

        final String bidder = eventNotificationRequest.getBidder();
        if (StringUtils.isBlank(bidder)) {
            throw new InvalidRequestException("bidder is required and can't be empty.");
        }

    }

    private void validateEventRequestQueryParams(String format) {
        if (format != null && ObjectUtils.notEqual(format, JPG) && ObjectUtils.notEqual(format, PNG)) {
            throw new InvalidRequestException("format when defined should has value of png or jpg.");
        }
    }

    private void respondWithBadStatus(RoutingContext context, String message) {
        context.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                .end(String.format("%s%s", "Request is invalid: ", message));
    }

    private void respondWithOkStatus(RoutingContext context, String format) {
        if (format != null) {
            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, format.equals(JPG) ? JPG_HEADER : PNG_HEADER)
                    .end(format.equals(JPG) ? Buffer.buffer(trackingPixelJpg) : Buffer.buffer(trackingPixelPng));
        } else {
            context.response().end();
        }
    }
}

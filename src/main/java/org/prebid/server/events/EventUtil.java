package org.prebid.server.events;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class EventUtil {

    private static final String TEMPLATE_URL = "%s/event?t=%s&b=%s&a=%s";

    // Required  query string parameters

    private static final String TYPE_PARAMETER = "t";
    private static final String WIN_TYPE = "win";
    private static final String IMP_TYPE = "imp";

    private static final String BID_ID_PARAMETER = "b";
    private static final String ACCOUNT_ID_PARAMETER = "a";

    // Optional query string parameters

    private static final String BIDDER_PARAMETER = "bidder";
    private static final String TIMESTAMP_PARAMETER = "ts";
    private static final String AUCTION_ID = "aid";

    private static final String FORMAT_PARAMETER = "f";
    private static final String BLANK_FORMAT = "b"; // default
    private static final String IMAGE_FORMAT = "i";

    private static final String INTEGRATION_PARAMETER = "int";
    private static final int INTEGRATION_PARAMETER_MAX_LENGTH = 64;

    private static final String ANALYTICS_PARAMETER = "x";
    private static final String ENABLED_ANALYTICS = "1"; // default
    private static final String DISABLED_ANALYTICS = "0";

    private static final String LINE_ITEM_ID_PARAMETER = "l";

    private EventUtil() {
    }

    public static void validateType(RoutingContext routingContext) {
        final String type = routingContext.request().params().get(TYPE_PARAMETER);
        if (ObjectUtils.notEqual(type, IMP_TYPE) && ObjectUtils.notEqual(type, WIN_TYPE)) {
            throw new IllegalArgumentException(String.format(
                    "Type '%s' is required query parameter. Possible values are %s and %s, but was %s",
                    TYPE_PARAMETER, WIN_TYPE, IMP_TYPE, type));
        }
    }

    public static void validateAccountId(RoutingContext routingContext) {
        final String accountId = routingContext.request().params().get(ACCOUNT_ID_PARAMETER);
        if (StringUtils.isBlank(accountId)) {
            throw new IllegalArgumentException(String.format(
                    "Account '%s' is required query parameter and can't be empty", ACCOUNT_ID_PARAMETER));
        }
    }

    public static void validateBidId(RoutingContext routingContext) {
        final String bidId = routingContext.request().params().get(BID_ID_PARAMETER);
        if (StringUtils.isBlank(bidId)) {
            throw new IllegalArgumentException(String.format(
                    "BidId '%s' is required query parameter and can't be empty", BID_ID_PARAMETER));
        }
    }

    public static void validateFormat(RoutingContext routingContext) {
        final String format = routingContext.request().params().get(FORMAT_PARAMETER);
        if (StringUtils.isNotEmpty(format) && !format.equals(BLANK_FORMAT) && !format.equals(IMAGE_FORMAT)) {
            throw new IllegalArgumentException(String.format(
                    "Format '%s' query parameter is invalid. Possible values are %s and %s, but was %s",
                    FORMAT_PARAMETER, BLANK_FORMAT, IMAGE_FORMAT, format));
        }
    }

    public static void validateAnalytics(RoutingContext routingContext) {
        final String analytics = routingContext.request().params().get(ANALYTICS_PARAMETER);
        if (StringUtils.isNotEmpty(analytics) && !analytics.equals(ENABLED_ANALYTICS)
                && !analytics.equals(DISABLED_ANALYTICS)) {
            throw new IllegalArgumentException(String.format(
                    "Analytics '%s' query parameter is invalid. Possible values are %s and %s, but was %s",
                    ANALYTICS_PARAMETER, ENABLED_ANALYTICS, DISABLED_ANALYTICS, analytics));
        }
    }

    public static void validateTimestamp(RoutingContext routingContext) {
        final String timestamp = StringUtils.stripToNull(routingContext.request().params().get(TIMESTAMP_PARAMETER));
        if (timestamp != null) {
            try {
                Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format(
                        "Timestamp '%s' query parameter is not valid number: %s", TIMESTAMP_PARAMETER, timestamp));
            }
        }
    }

    public static void validateIntegration(RoutingContext routingContext) {
        final String value = routingContext.request().getParam(INTEGRATION_PARAMETER);
        if (StringUtils.isNotEmpty(value)) {
            if (value.length() > INTEGRATION_PARAMETER_MAX_LENGTH) {
                throw new IllegalArgumentException(String.format(
                        "Integration '%s' query parameter is longer %s symbols: %s",
                        INTEGRATION_PARAMETER, INTEGRATION_PARAMETER_MAX_LENGTH, value));
            }
            for (int i = 0; i < value.length(); i++) {
                if (!Character.isLetterOrDigit(value.charAt(i)) && value.charAt(i) != '-' && value.charAt(i) != '_') {
                    throw new IllegalArgumentException(String.format(
                            "Integration '%s' query parameter is not valid: %s", INTEGRATION_PARAMETER, value));
                }
            }
        }
    }

    public static EventRequest from(RoutingContext routingContext) {
        final MultiMap queryParams = routingContext.request().params();

        final String typeAsString = queryParams.get(TYPE_PARAMETER);
        final EventRequest.Type type = typeAsString.equals(WIN_TYPE) ? EventRequest.Type.win : EventRequest.Type.imp;

        final EventRequest.Format format = Objects.equals(queryParams.get(FORMAT_PARAMETER), IMAGE_FORMAT)
                ? EventRequest.Format.image : EventRequest.Format.blank;

        final EventRequest.Analytics analytics = Objects.equals(DISABLED_ANALYTICS,
                queryParams.get(ANALYTICS_PARAMETER))
                ? EventRequest.Analytics.disabled : EventRequest.Analytics.enabled;

        final String timestampAsString = StringUtils.stripToNull(queryParams.get(TIMESTAMP_PARAMETER));
        final Long timestamp = timestampAsString != null ? Long.valueOf(timestampAsString) : null;

        final String auctionId = StringUtils.stripToNull(queryParams.get(AUCTION_ID));

        return EventRequest.builder()
                .type(type)
                .bidId(queryParams.get(BID_ID_PARAMETER))
                .auctionId(auctionId)
                .accountId(queryParams.get(ACCOUNT_ID_PARAMETER))
                .bidder(queryParams.get(BIDDER_PARAMETER))
                .timestamp(timestamp)
                .format(format)
                .analytics(analytics)
                .integration(queryParams.get(INTEGRATION_PARAMETER))
                .lineItemId(queryParams.get(LINE_ITEM_ID_PARAMETER))
                .build();
    }

    static String toUrl(String externalUrl, EventRequest eventRequest) {
        final String urlWithRequiredParameters = String.format(
                TEMPLATE_URL,
                externalUrl,
                eventRequest.getType(),
                eventRequest.getBidId(),
                eventRequest.getAccountId());

        return urlWithRequiredParameters + optionalParameters(eventRequest);
    }

    private static String optionalParameters(EventRequest eventRequest) {
        final StringBuilder result = new StringBuilder();

        // auction ID
        if (StringUtils.isNotEmpty(eventRequest.getAuctionId())) {
            result.append(nameValueAsQueryString(AUCTION_ID, eventRequest.getAuctionId()));
        }

        // timestamp
        if (eventRequest.getTimestamp() != null) {
            result.append(nameValueAsQueryString(TIMESTAMP_PARAMETER, eventRequest.getTimestamp().toString()));
        }

        // bidder
        if (StringUtils.isNotEmpty(eventRequest.getBidder())) {
            result.append(nameValueAsQueryString(BIDDER_PARAMETER, eventRequest.getBidder()));
        }

        // format
        if (eventRequest.getFormat() == EventRequest.Format.blank) {
            result.append(nameValueAsQueryString(FORMAT_PARAMETER, BLANK_FORMAT));
        } else if (eventRequest.getFormat() == EventRequest.Format.image) {
            result.append(nameValueAsQueryString(FORMAT_PARAMETER, IMAGE_FORMAT));
        }

        // integration
        result.append(nameValueAsQueryString(
                INTEGRATION_PARAMETER, StringUtils.stripToEmpty(eventRequest.getIntegration())));

        // analytics
        if (eventRequest.getAnalytics() == EventRequest.Analytics.enabled) {
            result.append(nameValueAsQueryString(ANALYTICS_PARAMETER, ENABLED_ANALYTICS));
        } else if (eventRequest.getAnalytics() == EventRequest.Analytics.disabled) {
            result.append(nameValueAsQueryString(ANALYTICS_PARAMETER, DISABLED_ANALYTICS));
        }

        result.append(StringUtils.isNotEmpty(eventRequest.getLineItemId())
                ? nameValueAsQueryString(LINE_ITEM_ID_PARAMETER, eventRequest.getLineItemId())
                : StringUtils.EMPTY); // skip parameter

        return result.toString();
    }

    private static String nameValueAsQueryString(String name, String value) {
        return "&" + name + "=" + value;
    }
}

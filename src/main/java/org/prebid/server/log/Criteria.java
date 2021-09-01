package org.prebid.server.log;

import io.vertx.core.logging.Logger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@Builder
@AllArgsConstructor
public class Criteria {

    private static final String TAG_SEPARATOR = "-";
    private static final String TAGGED_MESSAGE_PATTERN = "[%s]: %s";
    private static final String TAGGED_RESPONSE_PATTERN = "[%s]: %s - %s";
    public static final String BID_RESPONSE = "BidResponse";
    public static final String RESOLVED_BID_REQUEST = "Resolved BidRequest";

    String account;

    String bidder;

    String lineItemId;

    String tag;

    BiConsumer<Logger, Object> loggerLevel;

    public static Criteria create(String account, String bidder, String lineItemId,
                                  BiConsumer<Logger, Object> loggerLevel) {
        return new Criteria(account, bidder, lineItemId, makeTag(account, bidder, lineItemId), loggerLevel);
    }

    public void log(Criteria criteria, Logger logger, Object message, Consumer<Object> defaultLogger) {
        if (isMatched(criteria)) {
            loggerLevel.accept(logger, String.format(TAGGED_MESSAGE_PATTERN, tag, message));
        } else {
            defaultLogger.accept(message);
        }
    }

    public void logResponse(String bidResponse, Logger logger) {
        if (isMatchedToString(bidResponse)) {
            loggerLevel.accept(logger, String.format(TAGGED_RESPONSE_PATTERN, tag, BID_RESPONSE, bidResponse));
        }
    }

    public void logResponseAndRequest(String bidResponse, String bidRequest, Logger logger) {
        if (isMatchedToString(bidResponse + bidRequest)) {
            loggerLevel.accept(logger, String.format(TAGGED_RESPONSE_PATTERN, tag, BID_RESPONSE, bidResponse));
            loggerLevel.accept(logger, String.format(TAGGED_RESPONSE_PATTERN, tag, RESOLVED_BID_REQUEST, bidRequest));
        }
    }

    private boolean isMatched(Criteria criteria) {
        return criteria != null
                && ((account == null || account.equals(criteria.account))
                && (bidder == null || bidder.equals(criteria.bidder))
                && (lineItemId == null || lineItemId.equals(criteria.lineItemId)));
    }

    private boolean isMatchedToString(String value) {
        return (account == null || value.contains(account))
                && (bidder == null || value.contains(bidder))
                && (lineItemId == null || value.contains(lineItemId));
    }

    private static String makeTag(String account, String bidder, String lineItemId) {
        return Stream.of(account, bidder, lineItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(TAG_SEPARATOR));
    }

}

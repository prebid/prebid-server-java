package org.prebid.server.log;

import io.vertx.core.logging.Logger;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.function.BiConsumer;

public class Criteria {

    private static final String TAG_SEPARATOR = "-";
    private static final String TAGGED_RESPONSE_PATTERN = "[%s]: %s - %s";
    public static final String BID_RESPONSE = "BidResponse";
    public static final String RESOLVED_BID_REQUEST = "Resolved BidRequest";

    private final String account;
    private final String bidder;
    private final String tag;
    private final BiConsumer<Logger, Object> loggerLevel;

    private Criteria(String account, String bidder, BiConsumer<Logger, Object> loggerLevel) {
        this.account = account;
        this.bidder = bidder;
        this.tag = makeTag(account, bidder);
        this.loggerLevel = Objects.requireNonNull(loggerLevel);
    }

    public static Criteria create(String account, String bidder, BiConsumer<Logger, Object> loggerLevel) {
        return new Criteria(account, bidder, loggerLevel);
    }

    public void logResponse(String bidResponse, Logger logger) {
        if (isMatchedToString(bidResponse)) {
            loggerLevel.accept(logger, TAGGED_RESPONSE_PATTERN.formatted(tag, BID_RESPONSE, bidResponse));
        }
    }

    public void logResponseAndRequest(String bidResponse, String bidRequest, Logger logger) {
        if (isMatchedToString(bidResponse + bidRequest)) {
            loggerLevel.accept(logger, TAGGED_RESPONSE_PATTERN.formatted(tag, BID_RESPONSE, bidResponse));
            loggerLevel.accept(logger, TAGGED_RESPONSE_PATTERN.formatted(tag, RESOLVED_BID_REQUEST, bidRequest));
        }
    }

    private boolean isMatchedToString(String value) {
        return (account == null || value.contains(account))
                && (bidder == null || value.contains(bidder));
    }

    private static String makeTag(String account, String bidder) {
        if (account == null) {
            return StringUtils.defaultString(bidder);
        }
        if (bidder == null) {
            return account;
        }

        return account + TAG_SEPARATOR + bidder;
    }
}

package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.response.BidderDebug;

@AllArgsConstructor
@Value
public class ExchangeCall<T, R> {

    T request;

    R response;

    BidderDebug bidderDebug;

    String error;

    boolean timedOut;

    public static <T, R> ExchangeCall<T, R> error(BidderDebug bidderDebug, String error) {
        return new ExchangeCall<>(null, null, bidderDebug, error, false);
    }

    public static <T, R> ExchangeCall<T, R> timeout(BidderDebug bidderDebug, String error) {
        return new ExchangeCall<>(null, null, bidderDebug, error, true);
    }

    public static <T, R> ExchangeCall<T, R> success(T request, R response, BidderDebug bidderDebug) {
        return new ExchangeCall<>(request, response, bidderDebug, null, false);
    }

    public static <T, R> ExchangeCall<T, R> empty(BidderDebug bidderDebug) {
        return new ExchangeCall<>(null, null, bidderDebug, null, false);
    }
}

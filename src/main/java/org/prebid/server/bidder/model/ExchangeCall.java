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

    BidderError error;

    public static <T, R> ExchangeCall<T, R> error(BidderDebug bidderDebug, BidderError error) {
        return new ExchangeCall<>(null, null, bidderDebug, error);
    }

    public static <T, R> ExchangeCall<T, R> success(T request, R response, BidderDebug bidderDebug) {
        return new ExchangeCall<>(request, response, bidderDebug, null);
    }

    public static <T, R> ExchangeCall<T, R> empty(BidderDebug bidderDebug) {
        return new ExchangeCall<>(null, null, bidderDebug, null);
    }
}

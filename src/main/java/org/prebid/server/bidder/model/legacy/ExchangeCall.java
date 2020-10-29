package org.prebid.server.bidder.model.legacy;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.proto.response.legacy.BidderDebug;

@Deprecated
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

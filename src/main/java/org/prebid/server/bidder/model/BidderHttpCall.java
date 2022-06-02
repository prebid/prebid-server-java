package org.prebid.server.bidder.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BidderHttpCall<T> {

    HttpRequest<T> request;

    HttpResponse response;

    BidderCallType callType;

    BidderError error;

    public static <T> BidderHttpCall<T> storedHttp(HttpRequest<T> request, HttpResponse response) {
        return new BidderHttpCall<>(request, response, BidderCallType.STORED_BID_RESPONSE, null);
    }

    public static <T> BidderHttpCall<T> succeededHttp(HttpRequest<T> request,
                                                      HttpResponse response,
                                                      BidderError error) {

        return new BidderHttpCall<>(request, response, BidderCallType.HTTP, error);
    }

    public static <T> BidderHttpCall<T> failedHttp(HttpRequest<T> request, BidderError error) {
        return new BidderHttpCall<>(request, null, BidderCallType.HTTP, error);
    }

    public static <T> BidderHttpCall<T> unfinishedHttp(HttpRequest<T> request) {
        return new BidderHttpCall<>(request, null, BidderCallType.HTTP, null);
    }
}

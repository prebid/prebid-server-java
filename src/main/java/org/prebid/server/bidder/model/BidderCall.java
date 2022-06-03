package org.prebid.server.bidder.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BidderCall<T> {

    HttpRequest<T> request;

    HttpResponse response;

    BidderCallType callType;

    BidderError error;

    public static <T> BidderCall<T> storedHttp(HttpRequest<T> request, HttpResponse response) {
        return new BidderCall<>(request, response, BidderCallType.STORED_BID_RESPONSE, null);
    }

    public static <T> BidderCall<T> succeededHttp(HttpRequest<T> request,
                                                  HttpResponse response,
                                                  BidderError error) {

        return new BidderCall<>(request, response, BidderCallType.HTTP, error);
    }

    public static <T> BidderCall<T> failedHttp(HttpRequest<T> request, BidderError error) {
        return new BidderCall<>(request, null, BidderCallType.HTTP, error);
    }

    public static <T> BidderCall<T> unfinishedHttp(HttpRequest<T> request) {
        return new BidderCall<>(request, null, BidderCallType.HTTP, null);
    }
}

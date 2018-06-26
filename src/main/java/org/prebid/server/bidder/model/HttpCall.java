package org.prebid.server.bidder.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Value
public class HttpCall<T> {

    HttpRequest<T> request;

    HttpResponse response;

    BidderError error;

    public static <T> HttpCall<T> success(HttpRequest<T> request, HttpResponse response, BidderError error) {
        return new HttpCall<>(request, response, error);
    }

    public static <T> HttpCall<T> failure(HttpRequest<T> request, BidderError error) {
        return new HttpCall<>(request, null, error);
    }
}

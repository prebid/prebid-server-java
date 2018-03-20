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

    String error;

    boolean timedOut;

    public static <T> HttpCall<T> full(HttpRequest<T> request, HttpResponse response, String error) {
        return new HttpCall<>(request, response, error, false);
    }

    public static <T> HttpCall<T> partial(HttpRequest<T> request, String error, boolean timedOut) {
        return new HttpCall<>(request, null, error, timedOut);
    }
}

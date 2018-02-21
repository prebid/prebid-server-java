package org.rtb.vexing.bidder.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Value
public final class HttpCall {

    HttpRequest request;

    HttpResponse response;

    String error;

    public static HttpCall full(HttpRequest request, HttpResponse response, String error) {
        return new HttpCall(request, response, error);
    }

    public static HttpCall partial(HttpRequest request, String error) {
        return new HttpCall(request, null, error);
    }
}

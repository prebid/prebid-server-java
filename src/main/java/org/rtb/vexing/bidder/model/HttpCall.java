package org.rtb.vexing.bidder.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Packages together the fields needed to make an http request.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class HttpCall {

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

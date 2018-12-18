package org.prebid.server.bidder.model;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import lombok.Builder;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@Builder
@Value
public class HttpRequest<T> {

    HttpMethod method;

    String uri;

    String body;

    MultiMap headers;

    T payload;
}

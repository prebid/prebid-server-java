package org.prebid.server.bidder.model;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * Packages together the fields needed to make a http request.
 */
@Builder(toBuilder = true)
@Value
public class HttpRequest<T> {

    HttpMethod method;

    String uri;

    MultiMap headers;

    Set<String> impIds;

    byte[] body;

    T payload;
}

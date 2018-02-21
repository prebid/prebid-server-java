package org.rtb.vexing.bidder.model;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class HttpRequest {

    HttpMethod method;

    String uri;

    String body;

    MultiMap headers;
}

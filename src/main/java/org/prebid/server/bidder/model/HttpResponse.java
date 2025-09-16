package org.prebid.server.bidder.model;

import io.vertx.core.MultiMap;
import lombok.Value;

/**
 * Packages together information from the server's http response.
 */
@Value(staticConstructor = "of")
public class HttpResponse {

    int statusCode;

    MultiMap headers;

    String body;
}

package org.prebid.bidder.model;

import io.vertx.core.MultiMap;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together information from the server's http response.
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class HttpResponse {

    int statusCode;

    MultiMap headers;

    String body;
}

package org.prebid.server.vertx.http.model;

import io.vertx.core.MultiMap;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds Http client response data.
 * <p>
 * Should be created in "bodyHandler(...) after response has been read."
 */
@AllArgsConstructor(staticName = "of")
@Value
public class HttpClientResponse {

    int statusCode;

    MultiMap headers;

    String body;
}

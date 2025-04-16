package org.prebid.server.vertx.httpclient.model;

import io.vertx.core.MultiMap;
import lombok.Value;

/**
 * Holds Http client response data.
 * <p>
 * Should be created in "bodyHandler(...) after response has been read."
 */
@Value(staticConstructor = "of")
public class HttpClientResponse {

    int statusCode;

    MultiMap headers;

    String body;
}

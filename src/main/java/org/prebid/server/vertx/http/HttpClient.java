package org.prebid.server.vertx.http;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;

/**
 * Interface describes HTTP interactions.
 */
public interface HttpClient {

    /**
     * Makes HTTP request against given params
     * and executes response handler if request succeeds or exception handler otherwise.
     */
    void request(HttpMethod method, String url, MultiMap headers, String body, long timeoutMs,
                 Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler);
}

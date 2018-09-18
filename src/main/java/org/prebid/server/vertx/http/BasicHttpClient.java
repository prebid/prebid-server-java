package org.prebid.server.vertx.http;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;

import java.util.Objects;

/**
 * Simple wrapper around {@link HttpClient} with general functionality.
 */
public class BasicHttpClient implements HttpClient {

    private final io.vertx.core.http.HttpClient httpClient;

    public BasicHttpClient(io.vertx.core.http.HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public void request(HttpMethod method, String url, MultiMap headers, String body, long timeoutMs,
                        Handler<HttpClientResponse> responseHandler, Handler<Throwable> exceptionHandler) {

        final HttpClientRequest httpClientRequest = httpClient.requestAbs(method, url)
                .handler(responseHandler)
                .exceptionHandler(exceptionHandler);

        if (headers != null) {
            httpClientRequest.headers().addAll(headers);
        }

        if (timeoutMs > 0) {
            httpClientRequest.setTimeout(timeoutMs);
        }

        if (body != null) {
            httpClientRequest.end(body);
        } else {
            httpClientRequest.end();
        }
    }
}

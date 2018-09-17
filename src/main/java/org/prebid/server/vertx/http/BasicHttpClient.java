package org.prebid.server.vertx.http;

import io.vertx.core.Future;
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
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, String body,
                                              long timeoutMs, Handler<HttpClientResponse> responseHandler,
                                              Handler<Throwable> exceptionHandler) {
        final Future<HttpClientResponse> future = Future.future();

        final HttpClientRequest httpClientRequest = httpClient.requestAbs(method, url)
                .handler(response -> handleResponse(response, future, responseHandler))
                .exceptionHandler(exception -> handleException(exception, future, exceptionHandler));

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

        return future;
    }

    private static void handleResponse(HttpClientResponse response, Future<HttpClientResponse> future,
                                       Handler<HttpClientResponse> responseHandler) {
        future.tryComplete(response);
        if (responseHandler != null) {
            responseHandler.handle(response);
        }
    }

    private static void handleException(Throwable exception, Future<HttpClientResponse> future,
                                        Handler<Throwable> exceptionHandler) {
        future.tryFail(exception);
        if (exceptionHandler != null) {
            exceptionHandler.handle(exception);
        }
    }
}

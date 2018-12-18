package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Simple wrapper around {@link HttpClient} with general functionality.
 */
public class BasicHttpClient implements HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(BasicHttpClient.class);

    private final Vertx vertx;
    private final io.vertx.core.http.HttpClient httpClient;

    public BasicHttpClient(Vertx vertx, io.vertx.core.http.HttpClient httpClient) {
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, String body,
                                              long timeoutMs) {
        final Future<HttpClientResponse> future = Future.future();

        if (timeoutMs <= 0) {
            failResponse(new TimeoutException("Timeout has been exceeded"), future);
        } else {
            final HttpClientRequest httpClientRequest = httpClient.requestAbs(method, url);

            // Vert.x HttpClientRequest timeout doesn't aware of case when a part of the response body is received,
            // but remaining part is delayed. So, overall request/response timeout is involved to fix it.
            final long timerId = vertx.setTimer(timeoutMs, id -> handleTimeout(future, timeoutMs));

            httpClientRequest
                    .handler(response -> handleResponse(response, future, timerId))
                    .exceptionHandler(exception -> failResponse(exception, future, timerId));

            if (headers != null) {
                httpClientRequest.headers().addAll(headers);
            }

            if (body != null) {
                httpClientRequest.end(body);
            } else {
                httpClientRequest.end();
            }
        }

        return future;
    }

    private void handleTimeout(Future<HttpClientResponse> future, long timeoutMs) {
        if (!future.isComplete()) {
            failResponse(new TimeoutException(
                    String.format("Timeout period of %dms has been exceeded", timeoutMs)), future);
        }
    }

    private void handleResponse(io.vertx.core.http.HttpClientResponse response,
                                Future<HttpClientResponse> future, long timerId) {
        response
                .bodyHandler(buffer -> successResponse(buffer.toString(), response, future, timerId))
                .exceptionHandler(exception -> failResponse(exception, future, timerId));
    }

    private void successResponse(String body, io.vertx.core.http.HttpClientResponse response,
                                 Future<HttpClientResponse> future, long timerId) {
        vertx.cancelTimer(timerId);

        future.complete(HttpClientResponse.of(response.statusCode(), response.headers(), body));
    }

    private void failResponse(Throwable exception, Future<HttpClientResponse> future, long timerId) {
        vertx.cancelTimer(timerId);

        failResponse(exception, future);
    }

    private static void failResponse(Throwable exception, Future<HttpClientResponse> future) {
        logger.warn("HTTP client error", exception);

        future.tryFail(exception);
    }
}

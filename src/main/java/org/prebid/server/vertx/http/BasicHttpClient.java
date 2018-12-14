package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Data;
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
            final HttpClientRequest httpClientRequest = httpClient.requestAbs(method, url)
                    .setTimeout(timeoutMs); // request timeout

            // Vert.x HttpClientRequest timeout doesn't aware of case when a part of the response body is received,
            // but remaining response part is delayed. So, we involve overall response timeout to fix it.
            final long timerId = setResponseTimeout(timeoutMs, httpClientRequest, future);

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

    /**
     * Returns Vert.x Timer ID which fails the given {@link Future} in case of response obtaining is timed out.
     */
    private long setResponseTimeout(long timeoutMs, HttpClientRequest httpClientRequest,
                                    Future<HttpClientResponse> future) {
        final TimerId timerId = new TimerId();

        // Start timer after the connection is established to be in-sync with request timeout.
        httpClientRequest.connectionHandler(connection -> timerId.setValue(
                vertx.setTimer(timeoutMs, id -> handleResponseTimeout(httpClientRequest, future))));

        return timerId.getValue();
    }

    private void handleResponseTimeout(HttpClientRequest httpClientRequest, Future<HttpClientResponse> future) {
        if (!future.isComplete()) {
            failResponse(new TimeoutException("Timed out while waiting for response"), future);

            // Close connection after failing result.
            // Note: this will result "io.vertx.core.VertxException: Connection was closed" if close it first!
            httpClientRequest.reset();
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

    /**
     * Holds timer ID value.
     * <p>
     * This is because we cannot set primitive long inside HttpClientRequest.connectionHandler().
     */
    @Data
    private static class TimerId {

        long value;
    }
}

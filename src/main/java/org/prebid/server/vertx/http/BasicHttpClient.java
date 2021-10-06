package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Simple wrapper around {@link HttpClient} with general functionality.
 */
public class BasicHttpClient implements HttpClient {

    private final Vertx vertx;
    private final io.vertx.core.http.HttpClient httpClient;

    public BasicHttpClient(Vertx vertx, io.vertx.core.http.HttpClient httpClient) {
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, String body,
                                              long timeoutMs) {
        return request(method, url, headers, timeoutMs, body,
                (HttpClientRequest httpClientRequest) -> httpClientRequest.end(body));
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, byte[] body,
                                              long timeoutMs) {
        return request(method, url, headers, timeoutMs, body,
                (HttpClientRequest httpClientRequest) -> httpClientRequest.end(Buffer.buffer(body)));
    }

    private <T> Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, long timeoutMs,
                                                   T body, Consumer<HttpClientRequest> requestBodySetter) {
        final Promise<HttpClientResponse> promise = Promise.promise();

        if (timeoutMs <= 0) {
            failResponse(new TimeoutException("Timeout has been exceeded"), promise);
        } else {
            final HttpClientRequest httpClientRequest;
            try {
                httpClientRequest = httpClient.requestAbs(method, url);
            } catch (Exception e) {
                failResponse(e, promise);
                return promise.future();
            }

            // Vert.x HttpClientRequest timeout doesn't aware of case when a part of the response body is received,
            // but remaining part is delayed. So, overall request/response timeout is involved to fix it.
            final long timerId = vertx.setTimer(timeoutMs, id -> handleTimeout(promise, timeoutMs, httpClientRequest));

            httpClientRequest
                    .setFollowRedirects(true)
                    .handler(response -> handleResponse(response, promise, timerId))
                    .exceptionHandler(exception -> failResponse(exception, promise, timerId));

            if (headers != null) {
                httpClientRequest.headers().addAll(headers);
            }

            if (body != null) {
                requestBodySetter.accept(httpClientRequest);
            } else {
                httpClientRequest.end();
            }
        }
        return promise.future();
    }

    private void handleTimeout(Promise<HttpClientResponse> promise,
                               long timeoutMs,
                               HttpClientRequest httpClientRequest) {

        if (!promise.future().isComplete()) {
            failResponse(new TimeoutException(
                    String.format("Timeout period of %dms has been exceeded", timeoutMs)), promise);

            // Explicitly close connection, inspired by https://github.com/eclipse-vertx/vert.x/issues/2745
            httpClientRequest.reset();
        }
    }

    private void handleResponse(io.vertx.core.http.HttpClientResponse response,
                                Promise<HttpClientResponse> promise, long timerId) {
        response
                .bodyHandler(buffer -> successResponse(buffer.toString(), response, promise, timerId))
                .exceptionHandler(exception -> failResponse(exception, promise, timerId));
    }

    private void successResponse(String body, io.vertx.core.http.HttpClientResponse response,
                                 Promise<HttpClientResponse> promise, long timerId) {
        vertx.cancelTimer(timerId);

        promise.tryComplete(HttpClientResponse.of(response.statusCode(), response.headers(), body));
    }

    private void failResponse(Throwable exception, Promise<HttpClientResponse> promise, long timerId) {
        vertx.cancelTimer(timerId);

        failResponse(exception, promise);
    }

    private static void failResponse(Throwable exception, Promise<HttpClientResponse> promise) {
        promise.tryFail(exception);
    }
}

package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.vertx.http.model.HttpClientResponse;

/**
 * Interface describes HTTP interactions.
 */
@FunctionalInterface
public interface HttpClient {

    Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, String body, long timeoutMs);

    default Future<HttpClientResponse> get(String url, MultiMap headers, long timeoutMs) {
        return request(HttpMethod.GET, url, headers, null, timeoutMs);
    }

    default Future<HttpClientResponse> get(String url, long timeoutMs) {
        return request(HttpMethod.GET, url, null, null, timeoutMs);
    }

    default Future<HttpClientResponse> post(String url, MultiMap headers, String body, long timeoutMs) {
        return request(HttpMethod.POST, url, headers, body, timeoutMs);
    }

    default Future<HttpClientResponse> post(String url, String body, long timeoutMs) {
        return request(HttpMethod.POST, url, null, body, timeoutMs);
    }
}

package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.vertx.http.model.HttpClientResponse;

/**
 * Interface describes HTTP interactions.
 */
public interface HttpClient {

    Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers,
                                       String body, long timeoutMs, long maxResponseSize);

    Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers,
                                       byte[] body, long timeoutMs, long maxResponseSize);

    default Future<HttpClientResponse> request(HttpMethod method, String url,
                                               MultiMap headers, String body, long timeoutMs) {
        return request(method, url, headers, body, timeoutMs, Long.MAX_VALUE);
    }

    default Future<HttpClientResponse> request(HttpMethod method, String url,
                                               MultiMap headers, byte[] body, long timeoutMs) {
        return request(method, url, headers, body, timeoutMs, Long.MAX_VALUE);
    }

    default Future<HttpClientResponse> get(String url, long timeoutMs, long maxResponseSize) {
        return request(HttpMethod.GET, url, null, (String) null, timeoutMs, maxResponseSize);
    }

    default Future<HttpClientResponse> get(String url, MultiMap headers, long timeoutMs) {
        return request(HttpMethod.GET, url, headers, (String) null, timeoutMs);
    }

    default Future<HttpClientResponse> get(String url, long timeoutMs) {
        return request(HttpMethod.GET, url, null, (String) null, timeoutMs);
    }

    default Future<HttpClientResponse> post(String url, MultiMap headers, String body, long timeoutMs) {
        return request(HttpMethod.POST, url, headers, body, timeoutMs);
    }

    default Future<HttpClientResponse> post(String url, String body, long timeoutMs) {
        return request(HttpMethod.POST, url, null, body, timeoutMs);
    }
}

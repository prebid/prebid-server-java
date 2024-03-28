package org.prebid.server.vertx.httpclient;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Simple wrapper around {@link HttpClient} with general functionality.
 */
public class BasicHttpClient implements HttpClient {

    private final io.vertx.core.http.HttpClient httpClient;

    public BasicHttpClient(io.vertx.core.http.HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers,
                                              String body, long timeoutMs, long maxResponseSize) {

        return request(method, url, headers, timeoutMs, maxResponseSize, body != null ? body.getBytes() : null);
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers,
                                              byte[] body, long timeoutMs, long maxResponseSize) {

        return request(method, url, headers, timeoutMs, maxResponseSize, body);
    }

    private Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers,
                                               long timeoutMs, long maxResponseSize, byte[] body) {

        if (timeoutMs <= 0) {
            return Future.failedFuture(new TimeoutException("Timeout has been exceeded"));
        }

        final RequestOptions options = new RequestOptions()
                .setFollowRedirects(true)
                .setTimeout(timeoutMs)
                .setMethod(method)
                .setAbsoluteURI(url)
                .setHeaders(headers);

        return httpClient.request(options)
                .compose(request -> body != null ? request.send(Buffer.buffer(body)) : request.send())
                .compose(response -> toInternalResponse(response, maxResponseSize));
    }


    private Future<HttpClientResponse> toInternalResponse(io.vertx.core.http.HttpClientResponse response,
                                                          long maxResponseSize) {

        final String contentLength = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        final long responseBodySize = contentLength != null ? Long.parseLong(contentLength) : 0;
        if (responseBodySize > maxResponseSize) {
            return Future.failedFuture(new PreBidException(
                    "Response size %d exceeded %d bytes limit".formatted(responseBodySize, maxResponseSize)));
        }

        return response.body()
                .map(body -> HttpClientResponse.of(
                        response.statusCode(),
                        response.headers(),
                        body.toString(StandardCharsets.UTF_8)));

    }
}

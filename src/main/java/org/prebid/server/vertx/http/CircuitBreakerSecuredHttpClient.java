package org.prebid.server.vertx.http;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Wrapper over {@link HttpClient} with circuit breaker functionality.
 */
public class CircuitBreakerSecuredHttpClient implements HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredHttpClient.class);

    private final Function<String, CircuitBreaker> circuitBreakerCreator;
    private final Map<String, CircuitBreaker> circuitBreakerByName = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFailureByName = new ConcurrentHashMap<>();

    private final HttpClient httpClient;
    private final Metrics metrics;
    private final long openingIntervalMs;

    public CircuitBreakerSecuredHttpClient(Vertx vertx, HttpClient httpClient, Metrics metrics,
                                           int openingThreshold, long openingIntervalMs, long closingIntervalMs) {
        circuitBreakerCreator = name -> CircuitBreaker.create("http-client-circuit-breaker-" + name,
                Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setMaxFailures(openingThreshold)
                        .setResetTimeout(closingIntervalMs))
                .openHandler(ignored -> circuitOpened(name))
                .halfOpenHandler(ignored -> circuitHalfOpened(name))
                .closeHandler(ignored -> circuitClosed(name));

        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.openingIntervalMs = openingIntervalMs;

        logger.info("Initialized HTTP client with Circuit Breaker");
    }

    private void circuitOpened(String name) {
        logger.warn("Http client request to {0} is failed, circuit opened.", name);
        metrics.updateHttpClientCircuitBreakerMetric(true);
    }

    private void circuitHalfOpened(String name) {
        logger.warn("Http client request to {0} will try again, circuit half-opened.", name);
    }

    private void circuitClosed(String name) {
        logger.warn("Http client request to {0} becomes succeeded, circuit closed.", name);
        metrics.updateHttpClientCircuitBreakerMetric(false);
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, String body,
                                              long timeoutMs) {
        final String name = nameFrom(url);
        final CircuitBreaker breaker = circuitBreakerByName.computeIfAbsent(name, circuitBreakerCreator);

        return breaker.execute(future -> httpClient.request(method, url, headers, body, timeoutMs)
                .compose(response -> succeedBreaker(response, future))
                .recover(exception -> failBreaker(exception, future, name, breaker)));
    }

    private static Future<HttpClientResponse> succeedBreaker(HttpClientResponse response,
                                                             Future<HttpClientResponse> future) {
        future.complete(response);
        return future;
    }

    private Future<HttpClientResponse> failBreaker(Throwable exception, Future<HttpClientResponse> future,
                                                   String name, CircuitBreaker breaker) {
        ensureToIncrementFailureCount(name, breaker);

        future.fail(exception);
        return future;
    }

    /**
     * Reset failure counter to adjust open-circuit time frame.
     */
    private void ensureToIncrementFailureCount(String name, CircuitBreaker breaker) {
        final long currentTimeMillis = System.currentTimeMillis();

        final long lastFailureMs = lastFailureByName.computeIfAbsent(name, ignored -> currentTimeMillis);
        if (breaker.state() == CircuitBreakerState.CLOSED && currentTimeMillis - lastFailureMs > openingIntervalMs) {
            breaker.reset();
        }

        lastFailureByName.put(name, currentTimeMillis);
    }

    private static String nameFrom(String urlAsString) {
        final URL url = parseUrl(urlAsString);
        return url.getProtocol() + "://" + url.getHost()
                + (url.getPort() != -1 ? ":" + url.getPort() : "") + url.getPath();
    }

    private static URL parseUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new PreBidException(String.format("Invalid url: %s", url), e);
        }
    }
}

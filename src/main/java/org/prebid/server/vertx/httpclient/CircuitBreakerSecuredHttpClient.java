package org.prebid.server.vertx.httpclient;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Wrapper over {@link HttpClient} with circuit breaker functionality.
 */
public class CircuitBreakerSecuredHttpClient implements HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredHttpClient.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);
    private static final int LOG_PERIOD_SECONDS = 5;

    private final Function<String, CircuitBreaker> circuitBreakerCreator;
    private final Map<String, CircuitBreaker> circuitBreakerByName;

    private final HttpClient httpClient;

    public CircuitBreakerSecuredHttpClient(Vertx vertx,
                                           HttpClient httpClient,
                                           Metrics metrics,
                                           int openingThreshold,
                                           long openingIntervalMs,
                                           long closingIntervalMs,
                                           int idleExpireHours) {

        this.httpClient = Objects.requireNonNull(httpClient);

        circuitBreakerCreator = name -> createCircuitBreaker(
                name, vertx, openingThreshold, openingIntervalMs, closingIntervalMs, metrics);

        circuitBreakerByName = Caffeine.newBuilder()
                .expireAfterAccess(idleExpireHours, TimeUnit.HOURS)
                .<String, CircuitBreaker>removalListener((name, cb, cause) -> removeCircuitBreakerGauge(name, metrics))
                .build()
                .asMap();

        metrics.createHttpClientCircuitBreakerNumberGauge(circuitBreakerByName::size);

        logger.info("Initialized HTTP client with Circuit Breaker");
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method,
                                              String url,
                                              MultiMap headers,
                                              String body,
                                              long timeoutMs,
                                              long maxResponseSize) {

        return circuitBreakerByName.computeIfAbsent(nameFrom(url), circuitBreakerCreator)
                .execute(() -> httpClient.request(method, url, headers, body, timeoutMs, maxResponseSize));
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method,
                                              String url,
                                              MultiMap headers,
                                              byte[] body,
                                              long timeoutMs,
                                              long maxResponseSize) {
        return circuitBreakerByName.computeIfAbsent(nameFrom(url), circuitBreakerCreator)
                .execute(() -> httpClient.request(method, url, headers, body, timeoutMs, maxResponseSize));
    }

    private CircuitBreaker createCircuitBreaker(String name,
                                                Vertx vertx,
                                                int openingThreshold,
                                                long openingIntervalMs,
                                                long closingIntervalMs,
                                                Metrics metrics) {

        final CircuitBreakerOptions options = new CircuitBreakerOptions()
                .setNotificationPeriod(0)
                .setMaxFailures(openingThreshold)
                .setFailuresRollingWindow(openingIntervalMs)
                .setResetTimeout(closingIntervalMs);

        final CircuitBreaker circuitBreaker = CircuitBreaker.create(
                        "http_cb_" + name, Objects.requireNonNull(vertx), options)
                .openHandler(ignored -> circuitOpened(name))
                .halfOpenHandler(ignored -> circuitHalfOpened(name))
                .closeHandler(ignored -> circuitClosed(name));

        createCircuitBreakerGauge(name, circuitBreaker, metrics);

        return circuitBreaker;
    }

    private void createCircuitBreakerGauge(String name, CircuitBreaker circuitBreaker, Metrics metrics) {
        metrics.createHttpClientCircuitBreakerGauge(
                idFrom(name), () -> circuitBreaker.state() != CircuitBreakerState.CLOSED);
    }

    private void removeCircuitBreakerGauge(String name, Metrics metrics) {
        metrics.removeHttpClientCircuitBreakerGauge(idFrom(name));
    }

    private void circuitOpened(String name) {
        conditionalLogger.warn(
                "Http client request to %s is failed, circuit opened.".formatted(name),
                LOG_PERIOD_SECONDS,
                TimeUnit.SECONDS);
    }

    private void circuitHalfOpened(String name) {
        logger.warn("Http client request to {} will try again, circuit half-opened.", name);
    }

    private void circuitClosed(String name) {
        logger.warn("Http client request to {} becomes succeeded, circuit closed.", name);
    }

    private static String nameFrom(String urlAsString) {
        final URL url = parseUrl(urlAsString);
        return url.getProtocol() + "://" + url.getHost() + (url.getPort() != -1 ? ":" + url.getPort() : "");
    }

    private static String idFrom(String urlAsString) {
        return urlAsString
                .replaceAll("[^\\w]+", "_");
    }

    private static URL parseUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new PreBidException("Invalid url: " + url, e);
        }
    }
}

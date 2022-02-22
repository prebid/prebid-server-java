package org.prebid.server.vertx.http;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.CircuitBreaker;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
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
                                           int idleExpireHours,
                                           Clock clock) {

        this.httpClient = Objects.requireNonNull(httpClient);

        circuitBreakerCreator = name -> createCircuitBreaker(
                name, vertx, openingThreshold, openingIntervalMs, closingIntervalMs, clock, metrics);

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
                                              long timeoutMs) {

        return circuitBreakerByName.computeIfAbsent(nameFrom(url), circuitBreakerCreator)
                .execute(promise -> httpClient.request(method, url, headers, body, timeoutMs).onComplete(promise));
    }

    @Override
    public Future<HttpClientResponse> request(HttpMethod method, String url, MultiMap headers, byte[] body,
                                              long timeoutMs) {
        return circuitBreakerByName.computeIfAbsent(nameFrom(url), circuitBreakerCreator)
                .execute(promise -> httpClient.request(method, url, headers, body, timeoutMs).onComplete(promise));
    }

    private CircuitBreaker createCircuitBreaker(String name,
                                                Vertx vertx,
                                                int openingThreshold,
                                                long openingIntervalMs,
                                                long closingIntervalMs,
                                                Clock clock,
                                                Metrics metrics) {

        final CircuitBreaker circuitBreaker = new CircuitBreaker(
                "http_cb_" + name,
                Objects.requireNonNull(vertx),
                openingThreshold,
                openingIntervalMs,
                closingIntervalMs,
                Objects.requireNonNull(clock))
                .openHandler(ignored -> circuitOpened(name))
                .halfOpenHandler(ignored -> circuitHalfOpened(name))
                .closeHandler(ignored -> circuitClosed(name));

        createCircuitBreakerGauge(name, circuitBreaker, metrics);

        return circuitBreaker;
    }

    private void createCircuitBreakerGauge(String name, CircuitBreaker circuitBreaker, Metrics metrics) {
        metrics.createHttpClientCircuitBreakerGauge(idFrom(name), circuitBreaker::isOpen);
    }

    private void removeCircuitBreakerGauge(String name, Metrics metrics) {
        metrics.removeHttpClientCircuitBreakerGauge(idFrom(name));
    }

    private void circuitOpened(String name) {
        conditionalLogger.warn(String.format("Http client request to %s is failed, circuit opened.", name),
                LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    private void circuitHalfOpened(String name) {
        logger.warn("Http client request to {0} will try again, circuit half-opened.", name);
    }

    private void circuitClosed(String name) {
        logger.warn("Http client request to {0} becomes succeeded, circuit closed.", name);
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
            throw new PreBidException(String.format("Invalid url: %s", url), e);
        }
    }
}

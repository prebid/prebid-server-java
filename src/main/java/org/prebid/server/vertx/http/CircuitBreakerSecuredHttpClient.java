package org.prebid.server.vertx.http;

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
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, CircuitBreaker> circuitBreakerByName = new ConcurrentHashMap<>();

    private final HttpClient httpClient;
    private final Metrics metrics;

    public CircuitBreakerSecuredHttpClient(Vertx vertx, HttpClient httpClient, Metrics metrics,
                                           int openingThreshold, long openingIntervalMs, long closingIntervalMs,
                                           Clock clock) {
        circuitBreakerCreator = name -> new CircuitBreaker("http-client-circuit-breaker-" + name,
                Objects.requireNonNull(vertx), openingThreshold, openingIntervalMs, closingIntervalMs,
                Objects.requireNonNull(clock))
                .openHandler(ignored -> circuitOpened(name))
                .halfOpenHandler(ignored -> circuitHalfOpened(name))
                .closeHandler(ignored -> circuitClosed(name));

        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);

        logger.info("Initialized HTTP client with Circuit Breaker");
    }

    private void circuitOpened(String name) {
        conditionalLogger.warn(String.format("Http client request to %s is failed, circuit opened.", name),
                LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
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
        return circuitBreakerByName.computeIfAbsent(nameFrom(url), circuitBreakerCreator)
                .execute(promise -> httpClient.request(method, url, headers, body, timeoutMs).setHandler(promise));
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

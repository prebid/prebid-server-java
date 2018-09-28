package org.prebid.server.geolocation;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;

import java.util.Objects;

/**
 * Wrapper for geo location service with circuit breaker.
 */
public class CircuitBreakerSecuredGeoLocationService implements GeoLocationService {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredGeoLocationService.class);

    private final GeoLocationService geoLocationService;
    private final Metrics metrics;
    private final CircuitBreaker breaker;
    private final long openingIntervalMs;

    private long lastFailure;

    public CircuitBreakerSecuredGeoLocationService(Vertx vertx, GeoLocationService geoLocationService, Metrics metrics,
                                                   int openingThreshold, long openingIntervalMs,
                                                   long closingIntervalMs) {

        breaker = CircuitBreaker.create("geolocation-service-circuit-breaker", Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setMaxFailures(openingThreshold)
                        .setResetTimeout(closingIntervalMs))
                .openHandler(ignored -> circuitOpened())
                .halfOpenHandler(ignored -> circuitHalfOpened())
                .closeHandler(ignored -> circuitClosed());

        this.geoLocationService = Objects.requireNonNull(geoLocationService);
        this.metrics = Objects.requireNonNull(metrics);
        this.openingIntervalMs = openingIntervalMs;

        logger.info("Initialized GeoLocation service with Circuit Breaker");
    }

    private void circuitOpened() {
        logger.warn("GeoLocation service is unavailable, circuit opened.");
        metrics.updateGeoLocationCircuitBreakerMetric(true);
    }

    private void circuitHalfOpened() {
        logger.warn("GeoLocation service is ready to try again, circuit half-opened.");
    }

    private void circuitClosed() {
        logger.warn("GeoLocation service becomes working, circuit closed.");
        metrics.updateGeoLocationCircuitBreakerMetric(false);
    }

    @Override
    public Future<GeoInfo> lookup(String ip, Timeout timeout) {
        return breaker.execute(future -> geoLocationService.lookup(ip, timeout)
                .compose(response -> succeedBreaker(response, future))
                .recover(exception -> failBreaker(exception, future)));
    }

    private static Future<GeoInfo> succeedBreaker(GeoInfo response, Future<GeoInfo> future) {
        future.complete(response);
        return future;
    }

    private Future<GeoInfo> failBreaker(Throwable exception, Future<GeoInfo> future) {
        ensureToIncrementFailureCount();

        future.fail(exception);
        return future;
    }

    /**
     * Reset failure counter to adjust open-circuit time frame.
     */
    private void ensureToIncrementFailureCount() {
        final long currentTimeMillis = System.currentTimeMillis();

        if (breaker.state() == CircuitBreakerState.CLOSED && currentTimeMillis - lastFailure > openingIntervalMs) {
            breaker.reset();
        }

        lastFailure = currentTimeMillis;
    }
}

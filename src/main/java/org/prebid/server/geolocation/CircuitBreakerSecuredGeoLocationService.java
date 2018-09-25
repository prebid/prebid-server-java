package org.prebid.server.geolocation;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
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

    public CircuitBreakerSecuredGeoLocationService(Vertx vertx, GeoLocationService geoLocationService, Metrics metrics,
                                                   int maxFailures, long timeoutMs, long resetTimeoutMs) {

        breaker = CircuitBreaker.create("geolocation-service-circuit-breaker", Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setMaxFailures(maxFailures)
                        .setTimeout(timeoutMs)
                        .setResetTimeout(resetTimeoutMs))
                .openHandler(ignored -> circuitOpened())
                .halfOpenHandler(ignored -> circuitHalfOpened())
                .closeHandler(ignored -> circuitClosed());

        this.geoLocationService = Objects.requireNonNull(geoLocationService);
        this.metrics = Objects.requireNonNull(metrics);

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
        return breaker.execute(future -> geoLocationService.lookup(ip, timeout).setHandler(future));
    }
}

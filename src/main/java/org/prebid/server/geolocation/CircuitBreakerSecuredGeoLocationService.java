package org.prebid.server.geolocation;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.CircuitBreaker;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper for geo location service with circuit breaker.
 */
public class CircuitBreakerSecuredGeoLocationService implements GeoLocationService {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredGeoLocationService.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);
    private static final int LOG_PERIOD_SECONDS = 5;

    private final CircuitBreaker breaker;
    private final GeoLocationService geoLocationService;
    private final Metrics metrics;

    public CircuitBreakerSecuredGeoLocationService(Vertx vertx, GeoLocationService geoLocationService, Metrics metrics,
                                                   int openingThreshold, long openingIntervalMs,
                                                   long closingIntervalMs, Clock clock) {

        breaker = new CircuitBreaker("geolocation-service-circuit-breaker", Objects.requireNonNull(vertx),
                openingThreshold, openingIntervalMs, closingIntervalMs, Objects.requireNonNull(clock))
                .openHandler(ignored -> circuitOpened())
                .halfOpenHandler(ignored -> circuitHalfOpened())
                .closeHandler(ignored -> circuitClosed());

        this.geoLocationService = Objects.requireNonNull(geoLocationService);
        this.metrics = Objects.requireNonNull(metrics);

        logger.info("Initialized GeoLocation service with Circuit Breaker");
    }

    private void circuitOpened() {
        conditionalLogger.warn("GeoLocation service is unavailable, circuit opened.",
                LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
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
        return breaker.execute(promise -> geoLocationService.lookup(ip, timeout).setHandler(promise));
    }
}

package org.prebid.server.geolocation;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;

import java.util.Objects;

/**
 * Wrapper for geolocation service with circuit breaker.
 */
public class CircuitBreakerSecuredGeoLocationService implements GeoLocationService {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredGeoLocationService.class);

    private final GeoLocationService geoLocationService;
    private final CircuitBreaker breaker;

    public CircuitBreakerSecuredGeoLocationService(Vertx vertx,
                                                   GeoLocationService geoLocationService,
                                                   Metrics metrics,
                                                   int openingThreshold,
                                                   long openingIntervalMs,
                                                   long closingIntervalMs) {

        this.geoLocationService = Objects.requireNonNull(geoLocationService);

        breaker = CircuitBreaker.create(
                "geo_cb",
                Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setNotificationPeriod(0)
                        .setMaxFailures(openingThreshold)
                        .setFailuresRollingWindow(openingIntervalMs)
                        .setResetTimeout(closingIntervalMs));

        metrics.createGeoLocationCircuitBreakerGauge(() -> breaker.state() != CircuitBreakerState.CLOSED);

        logger.info("Initialized GeoLocation service with Circuit Breaker");
    }

    @Override
    public Future<GeoInfo> lookup(String ip, Timeout timeout) {
        return breaker.execute(() -> geoLocationService.lookup(ip, timeout));
    }
}

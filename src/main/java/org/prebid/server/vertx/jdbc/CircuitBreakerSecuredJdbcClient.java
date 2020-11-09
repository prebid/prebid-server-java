package org.prebid.server.vertx.jdbc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import org.prebid.server.execution.Timeout;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.CircuitBreaker;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * JDBC Client wrapped by {@link CircuitBreaker} to achieve robust operating.
 */
public class CircuitBreakerSecuredJdbcClient implements JdbcClient {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredJdbcClient.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);
    private static final int LOG_PERIOD_SECONDS = 5;

    private final CircuitBreaker breaker;
    private final JdbcClient jdbcClient;
    private final Metrics metrics;

    public CircuitBreakerSecuredJdbcClient(Vertx vertx, JdbcClient jdbcClient, Metrics metrics,
                                           int openingThreshold, long openingIntervalMs, long closingIntervalMs,
                                           Clock clock) {

        breaker = new CircuitBreaker("db_cb", Objects.requireNonNull(vertx),
                openingThreshold, openingIntervalMs, closingIntervalMs, Objects.requireNonNull(clock))
                .openHandler(ignored -> circuitOpened())
                .halfOpenHandler(ignored -> circuitHalfOpened())
                .closeHandler(ignored -> circuitClosed());

        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.metrics = Objects.requireNonNull(metrics);

        logger.info("Initialized JDBC client with Circuit Breaker");
    }

    private void circuitOpened() {
        conditionalLogger.warn("Database is unavailable, circuit opened.",
                LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
        metrics.updateDatabaseCircuitBreakerMetric(true);
    }

    private void circuitHalfOpened() {
        logger.warn("Database is ready to try again, circuit half-opened.");
    }

    private void circuitClosed() {
        logger.warn("Database becomes working, circuit closed.");
        metrics.updateDatabaseCircuitBreakerMetric(false);
    }

    @Override
    public <T> Future<T> executeQuery(String query, List<Object> params, Function<ResultSet, T> mapper,
                                      Timeout timeout) {
        return breaker.execute(promise -> jdbcClient.executeQuery(query, params, mapper, timeout).setHandler(promise));
    }
}

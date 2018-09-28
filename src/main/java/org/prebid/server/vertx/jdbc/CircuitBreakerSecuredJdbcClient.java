package org.prebid.server.vertx.jdbc;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import org.prebid.server.execution.Timeout;
import org.prebid.server.metric.Metrics;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * JDBC Client wrapped by {@link CircuitBreaker} to achieve robust operating.
 */
public class CircuitBreakerSecuredJdbcClient implements JdbcClient {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredJdbcClient.class);

    private final JdbcClient jdbcClient;
    private final Metrics metrics;
    private final CircuitBreaker breaker;
    private final long openingIntervalMs;

    private long lastFailure;

    public CircuitBreakerSecuredJdbcClient(Vertx vertx, JdbcClient jdbcClient, Metrics metrics,
                                           int openingThreshold, long openingIntervalMs, long closingIntervalMs) {

        breaker = CircuitBreaker.create("jdbc-client-circuit-breaker", Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setMaxFailures(openingThreshold)
                        .setResetTimeout(closingIntervalMs))
                .openHandler(ignored -> circuitOpened())
                .halfOpenHandler(ignored -> circuitHalfOpened())
                .closeHandler(ignored -> circuitClosed());

        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.openingIntervalMs = openingIntervalMs;

        logger.info("Initialized JDBC client with Circuit Breaker");
    }

    private void circuitOpened() {
        logger.warn("Database is unavailable, circuit opened.");
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
    public <T> Future<T> executeQuery(String query, List<String> params, Function<ResultSet, T> mapper,
                                      Timeout timeout) {
        return breaker.execute(future -> jdbcClient.executeQuery(query, params, mapper, timeout)
                .compose(response -> succeedBreaker(response, future))
                .recover(exception -> failBreaker(exception, future)));
    }

    private static <T> Future<T> succeedBreaker(T response, Future<T> future) {
        future.complete(response);
        return future;
    }

    private <T> Future<T> failBreaker(Throwable exception, Future<T> future) {
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

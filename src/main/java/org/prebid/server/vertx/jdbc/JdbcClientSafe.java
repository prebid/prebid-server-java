package org.prebid.server.vertx.jdbc;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
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
 * JDBC Client wrapped by {@link CircuitBreaker} to achieve robust behavior
 */
public class JdbcClientSafe implements JdbcClient {

    private static final Logger logger = LoggerFactory.getLogger(JdbcClientSafe.class);

    private final JdbcClientBasic jdbcClientBasic;
    private final Metrics metrics;
    private final CircuitBreaker breaker;

    public JdbcClientSafe(Vertx vertx, JdbcClientBasic jdbcClientBasic, Metrics metrics,
                          int maxFailures, long timeoutMs, long resetTimeoutMs) {

        breaker = CircuitBreaker.create("jdbc-client-circuit-breaker", Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setMaxFailures(maxFailures) // number of failure before opening the circuit
                        .setTimeout(timeoutMs) // consider a failure if the operation does not succeed in time
                        .setResetTimeout(resetTimeoutMs)) // time spent in open state before attempting to re-try
                .openHandler(ignored -> circuitOpened())
                .closeHandler(ignored -> circuitClosed());

        this.jdbcClientBasic = Objects.requireNonNull(jdbcClientBasic);
        this.metrics = Objects.requireNonNull(metrics);
    }

    private void circuitOpened() {
        logger.warn("Database unavailable, circuit opened.");
        metrics.updateDatabaseCircuitBreakerMetric(true);
    }

    private void circuitClosed() {
        logger.warn("Database becomes working, circuit closed.");
        metrics.updateDatabaseCircuitBreakerMetric(false);
    }

    @Override
    public Future<Void> initialize() {
        logger.info("Initializing JDBC client with Circuit Breaker");
        return jdbcClientBasic.initialize();
    }

    @Override
    public <T> Future<T> executeQuery(String query, List<String> params, Function<ResultSet, T> mapper,
                                      Timeout timeout) {
        return breaker.execute(future -> jdbcClientBasic.executeQuery(query, params, mapper, timeout)
                .setHandler(future));
    }
}

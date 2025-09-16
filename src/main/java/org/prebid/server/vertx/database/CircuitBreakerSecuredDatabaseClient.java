package org.prebid.server.vertx.database;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.CircuitBreaker;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Database Client wrapped by {@link CircuitBreaker} to achieve robust operating.
 */
public class CircuitBreakerSecuredDatabaseClient implements DatabaseClient {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredDatabaseClient.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);
    private static final int LOG_PERIOD_SECONDS = 5;

    private final DatabaseClient databaseClient;
    private final CircuitBreaker breaker;

    public CircuitBreakerSecuredDatabaseClient(Vertx vertx,
                                               DatabaseClient databaseClient,
                                               Metrics metrics,
                                               int openingThreshold,
                                               long openingIntervalMs,
                                               long closingIntervalMs,
                                               Clock clock) {

        this.databaseClient = Objects.requireNonNull(databaseClient);

        breaker = new CircuitBreaker(
                "db_cb",
                Objects.requireNonNull(vertx),
                openingThreshold,
                openingIntervalMs,
                closingIntervalMs,
                Objects.requireNonNull(clock))
                .openHandler(ignored -> circuitOpened())
                .halfOpenHandler(ignored -> circuitHalfOpened())
                .closeHandler(ignored -> circuitClosed());

        metrics.createDatabaseCircuitBreakerGauge(breaker::isOpen);

        logger.info("Initialized database client with Circuit Breaker");
    }

    @Override
    public <T> Future<T> executeQuery(String query,
                                      List<Object> params,
                                      Function<RowSet<Row>, T> mapper,
                                      Timeout timeout) {

        return breaker.execute(
                promise -> databaseClient.executeQuery(query, params, mapper, timeout).onComplete(promise));
    }

    private void circuitOpened() {
        conditionalLogger.warn("Database is unavailable, circuit opened.", LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    private void circuitHalfOpened() {
        logger.warn("Database is ready to try again, circuit half-opened.");
    }

    private void circuitClosed() {
        logger.warn("Database becomes working, circuit closed.");
    }
}

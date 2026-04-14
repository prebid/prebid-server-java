package org.prebid.server.vertx.database;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Database Client wrapped by CircuitBreaker to achieve robust operating.
 */
public class CircuitBreakerSecuredDatabaseClient implements DatabaseClient {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerSecuredDatabaseClient.class);

    private final DatabaseClient databaseClient;
    private final CircuitBreaker breaker;

    public CircuitBreakerSecuredDatabaseClient(Vertx vertx,
                                               DatabaseClient databaseClient,
                                               Metrics metrics,
                                               int openingThreshold,
                                               long openingIntervalMs,
                                               long closingIntervalMs) {

        this.databaseClient = Objects.requireNonNull(databaseClient);

        breaker = CircuitBreaker.create(
                "db_cb",
                Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setNotificationPeriod(0)
                        .setMaxFailures(openingThreshold)
                        .setFailuresRollingWindow(openingIntervalMs)
                        .setResetTimeout(closingIntervalMs));

        metrics.createDatabaseCircuitBreakerGauge(() -> breaker.state() != CircuitBreakerState.CLOSED);

        logger.info("Initialized database client with Circuit Breaker");
    }

    @Override
    public <T> Future<T> executeQuery(String query,
                                      List<Object> params,
                                      Function<RowSet<Row>, T> mapper,
                                      Timeout timeout) {

        return breaker.execute(() -> databaseClient.executeQuery(query, params, mapper, timeout));
    }
}

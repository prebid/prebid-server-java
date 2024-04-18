package org.prebid.server.vertx.jdbc;

import io.vertx.core.Future;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import org.prebid.server.execution.Timeout;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Wrapper over {@link JDBCClient} that supports setting query timeout in milliseconds.
 */
public class BasicJdbcClient implements JdbcClient {

    private static final Logger logger = LoggerFactory.getLogger(BasicJdbcClient.class);

    private final Pool pool;
    private final Metrics metrics;
    private final Clock clock;

    public BasicJdbcClient(Pool pool, Metrics metrics, Clock clock) {
        this.pool = Objects.requireNonNull(pool);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Triggers connection creation. Should be called during application initialization to detect connection issues as
     * early as possible.
     * <p>
     * Must be called on Vertx event loop thread.
     */
    public Future<Void> initialize() {
        return pool.getConnection()
                .recover(BasicJdbcClient::logConnectionError)
                .mapEmpty();
    }

    @Override
    public <T> Future<T> executeQuery(String query,
                                      List<Object> params,
                                      Function<RowSet<Row>, T> mapper,
                                      Timeout timeout) {

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return Future.failedFuture(timeoutException());
        }
        final long startTime = clock.millis();

        return pool.getConnection()
                .recover(BasicJdbcClient::logConnectionError)
                .compose(connection -> makeQuery(connection, query, params))
                .timeout(remainingTimeout, TimeUnit.MILLISECONDS)
                .recover(this::handleFailure)
                .onComplete(result -> metrics.updateDatabaseQueryTimeMetric(clock.millis() - startTime))
                .map(mapper);
    }

    private Future<RowSet<Row>> handleFailure(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return Future.failedFuture(timeoutException());
        }

        return Future.failedFuture(throwable);
    }

    private static Future<SqlConnection> logConnectionError(Throwable exception) {
        logger.warn("Cannot connect to database", exception);
        return Future.failedFuture(exception);
    }

    /**
     * Performs query to DB.
     */
    private static Future<RowSet<Row>> makeQuery(SqlConnection connection, String query, List<Object> params) {
        return connection.preparedQuery(query).execute(Tuple.tuple(params)).onComplete(ignored -> connection.close());
    }

    private static TimeoutException timeoutException() {
        return new TimeoutException("Timed out while executing SQL query");
    }
}

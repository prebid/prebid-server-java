package org.prebid.server.vertx.jdbc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
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
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Wrapper over {@link JDBCClient} that supports setting query timeout in milliseconds.
 */
public class BasicJdbcClient implements JdbcClient {

    private static final Logger logger = LoggerFactory.getLogger(BasicJdbcClient.class);

    private final Vertx vertx;
    private final Pool pool;
    private final Metrics metrics;
    private final Clock clock;

    public BasicJdbcClient(Vertx vertx, Pool pool, Metrics metrics, Clock clock) {
        this.vertx = Objects.requireNonNull(vertx);
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
        final Promise<SqlConnection> connectionPromise = Promise.promise();
        pool.getConnection(connectionPromise);
        return connectionPromise.future()
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
        final Promise<RowSet<Row>> queryResultPromise = Promise.promise();

        // timeout implementation is inspired by this answer:
        // https://groups.google.com/d/msg/vertx/eSf3AQagGGU/K7pztnjLc_EJ
        final long timerId = vertx.setTimer(remainingTimeout, id -> timedOutResult(queryResultPromise, startTime));

        final Promise<SqlConnection> connectionPromise = Promise.promise();
        pool.getConnection(connectionPromise);
        connectionPromise.future()
                .recover(BasicJdbcClient::logConnectionError)
                .compose(connection -> makeQuery(connection, query, params))
                .onComplete(result -> handleResult(result, queryResultPromise, timerId, startTime));

        return queryResultPromise.future().map(mapper);
    }

    /**
     * Fails result {@link Promise} with timeout exception.
     */
    private void timedOutResult(Promise<RowSet<Row>> queryResultPromise, long startTime) {
        // no need for synchronization since timer is fired on the same event loop thread
        if (!queryResultPromise.future().isComplete()) {
            metrics.updateDatabaseQueryTimeMetric(clock.millis() - startTime);
            queryResultPromise.fail(timeoutException());
        }
    }

    private static Future<SqlConnection> logConnectionError(Throwable exception) {
        logger.warn("Cannot connect to database", exception);
        return Future.failedFuture(exception);
    }

    /**
     * Performs query to DB.
     */
    private static Future<RowSet<Row>> makeQuery(SqlConnection connection, String query, List<Object> params) {
        final Promise<RowSet<Row>> resultSetPromise = Promise.promise();
        connection.preparedQuery(query).execute(Tuple.tuple(params), ar -> {
            connection.close();
            resultSetPromise.handle(ar);
        });
        return resultSetPromise.future();
    }

    /**
     * Propagates responded {@link ResultSet} (or failure) to result {@link Promise}.
     */
    private void handleResult(AsyncResult<RowSet<Row>> result,
                              Promise<RowSet<Row>> queryResultPromise,
                              long timerId,
                              long startTime) {

        vertx.cancelTimer(timerId);

        // check is to avoid harmless exception if timeout exceeds before successful result becomes ready
        if (!queryResultPromise.future().isComplete()) {
            metrics.updateDatabaseQueryTimeMetric(clock.millis() - startTime);
            queryResultPromise.handle(result);
        }
    }

    private static TimeoutException timeoutException() {
        return new TimeoutException("Timed out while executing SQL query");
    }
}

package org.prebid.server.vertx.jdbc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.prebid.server.execution.Timeout;
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

    private final Vertx vertx;
    private final JDBCClient jdbcClient;
    private final Metrics metrics;
    private final Clock clock;

    public BasicJdbcClient(Vertx vertx, JDBCClient jdbcClient, Metrics metrics, Clock clock) {
        this.vertx = Objects.requireNonNull(vertx);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
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
        final Future<SQLConnection> result = Future.future();
        jdbcClient.getConnection(result.completer());
        return result.mapEmpty();
    }

    @Override
    public <T> Future<T> executeQuery(String query, List<String> params, Function<ResultSet, T> mapper,
                                      Timeout timeout) {
        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return Future.failedFuture(timeoutException());
        }
        final long startTime = clock.millis();
        final Future<ResultSet> queryResultFuture = Future.future();

        // timeout implementation is inspired by this answer:
        // https://groups.google.com/d/msg/vertx/eSf3AQagGGU/K7pztnjLc_EJ
        final long timerId = vertx.setTimer(remainingTimeout, id -> timedOutResult(queryResultFuture, startTime));

        final Future<SQLConnection> connectionFuture = Future.future();
        jdbcClient.getConnection(connectionFuture.completer());
        connectionFuture
                .compose(connection -> makeQuery(connection, query, params))
                .setHandler(result -> handleResult(result, queryResultFuture, timerId, startTime));

        return queryResultFuture.map(mapper);
    }

    /**
     * Fails result {@link Future} with timeout exception.
     */
    private void timedOutResult(Future<ResultSet> queryResultFuture, long startTime) {
        // no need for synchronization since timer is fired on the same event loop thread
        if (!queryResultFuture.isComplete()) {
            metrics.updateDatabaseQueryTimeMetric(clock.millis() - startTime);
            queryResultFuture.fail(timeoutException());
        }
    }

    /**
     * Performs query to DB.
     */
    private static Future<ResultSet> makeQuery(SQLConnection connection, String query, List<String> params) {
        final Future<ResultSet> resultSetFuture = Future.future();
        connection.queryWithParams(query, new JsonArray(params),
                ar -> {
                    connection.close();
                    resultSetFuture.handle(ar);
                });
        return resultSetFuture;
    }

    /**
     * Propagates responded {@link ResultSet} (or failure) to result {@link Future}.
     */
    private void handleResult(AsyncResult<ResultSet> result, Future<ResultSet> queryResultFuture, long timerId,
                              long startTime) {
        vertx.cancelTimer(timerId);

        // check is to avoid harmless exception if timeout exceeds before successful result becomes ready
        if (!queryResultFuture.isComplete()) {
            metrics.updateDatabaseQueryTimeMetric(clock.millis() - startTime);
            queryResultFuture.handle(result);
        }
    }

    private static TimeoutException timeoutException() {
        return new TimeoutException("Timed out while executing SQL query");
    }
}

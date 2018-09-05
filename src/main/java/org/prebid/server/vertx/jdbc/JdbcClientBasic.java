package org.prebid.server.vertx.jdbc;

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
public class JdbcClientBasic implements JdbcClient {

    private final Vertx vertx;
    private final JDBCClient jdbcClient;
    private final Metrics metrics;
    private final Clock clock;

    public JdbcClientBasic(Vertx vertx, JDBCClient jdbcClient, Metrics metrics, Clock clock) {
        this.vertx = Objects.requireNonNull(vertx);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
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

        // timeout implementation is inspired by this answer:
        // https://groups.google.com/d/msg/vertx/eSf3AQagGGU/K7pztnjLc_EJ
        final Future<ResultSet> queryResultFuture = Future.future();
        final long timerId = vertx.setTimer(remainingTimeout, id -> {
            // no need for synchronization since timer is fired on the same event loop thread
            if (!queryResultFuture.isComplete()) {
                metrics.updateDatabaseQueryTimeMetric(clock.millis() - startTime);
                queryResultFuture.fail(timeoutException());
            }
        });

        final Future<SQLConnection> connectionFuture = Future.future();
        jdbcClient.getConnection(connectionFuture.completer());

        connectionFuture
                .compose(connection -> {
                    final Future<ResultSet> resultSetFuture = Future.future();
                    connection.queryWithParams(query, new JsonArray(params),
                            ar -> {
                                connection.close();
                                resultSetFuture.handle(ar);
                            });
                    return resultSetFuture;
                })
                .setHandler(ar -> {
                    vertx.cancelTimer(timerId);
                    // check is to avoid harmless exception if timeout exceeds before successful result becomes ready
                    if (!queryResultFuture.isComplete()) {
                        metrics.updateDatabaseQueryTimeMetric(clock.millis() - startTime);
                        queryResultFuture.handle(ar);
                    }
                });

        return queryResultFuture.map(mapper);
    }

    private static TimeoutException timeoutException() {
        return new TimeoutException("Timed out while executing SQL query");
    }
}

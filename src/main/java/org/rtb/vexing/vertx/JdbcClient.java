package org.rtb.vexing.vertx;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Wrapper over {@link JDBCClient} that supports setting query timeout in milliseconds.
 */
public class JdbcClient {

    private final Vertx vertx;
    private final JDBCClient jdbcClient;

    public JdbcClient(Vertx vertx, JDBCClient jdbcClient) {
        this.vertx = Objects.requireNonNull(vertx);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    /**
     * Triggers connection creation. Should be called during application initialization to detect connection issues as
     * early as possible.
     */
    public Future<Void> initialize() {
        final Future<SQLConnection> result = Future.future();
        jdbcClient.getConnection(result.completer());
        return result.mapEmpty();
    }

    /**
     * Executes query with parameters and returns {@link Future<T>} eventually holding result mapped to a model
     * object by provided mapper
     */
    public <T> Future<T> executeQuery(String query, List<String> params, Function<ResultSet, T> mapper) {
        // timeout implementation is inspired by this answer:
        // https://groups.google.com/d/msg/vertx/eSf3AQagGGU/K7pztnjLc_EJ
        final Future<ResultSet> queryResultFuture = Future.future();
        // FIXME: timeout
        final int timeout = 500;
        final long timerId = vertx.setTimer(timeout, id -> {
            // no need for synchronization since timer is fired on the same event loop thread
            if (!queryResultFuture.isComplete()) {
                queryResultFuture.fail(new TimeoutException("Timed out while executing SQL query"));
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
                    // this may produce harmless exception if timeout exceeds before successful result becomes ready
                    queryResultFuture.handle(ar);
                });

        return queryResultFuture.map(mapper);
    }
}

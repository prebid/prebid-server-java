package org.prebid.server.health;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DatabaseChecker extends AbstractHealthCheck {

    private static final String NAME = "database";

    private final JDBCClient jdbcClient;

    private Map<String, Object> lastStatusResponse;

    public DatabaseChecker(Vertx vertx, JDBCClient jdbcClient, long refreshPeriod) {
        super(vertx, refreshPeriod);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public Map<String, Object> status() {
        return lastStatusResponse;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    void checkStatus() {
        final Future<SQLConnection> connectionFuture = Future.future();
        jdbcClient.getConnection(connectionFuture.completer());
        connectionFuture.setHandler(result -> {
            final Status status = result.succeeded() ? Status.UP : Status.DOWN;
            final Map<String, Object> lastStatus = new HashMap<>();
            lastStatus.put("status", status);
            lastStatus.put("last_updated", ZonedDateTime.now(Clock.systemUTC()));
            lastStatusResponse = lastStatus;
        });
    }
}

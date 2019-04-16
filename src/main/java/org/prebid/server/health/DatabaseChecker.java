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
    private static final String STATUS_KEY = "status";
    private static final String LAST_UPDATED_KEY = "last_updated";

    private final JDBCClient jdbcClient;

    private Map<String, Object> status;

    public DatabaseChecker(Vertx vertx, JDBCClient jdbcClient, long refreshPeriod) {
        super(vertx, refreshPeriod);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public Map<String, Object> status() {
        return status;
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
            final Map<String, Object> lastStatus = new HashMap<>();
            lastStatus.put(STATUS_KEY, result.succeeded() ? Status.UP : Status.DOWN);
            lastStatus.put(LAST_UPDATED_KEY, ZonedDateTime.now(Clock.systemUTC()));
            status = lastStatus;
        });
    }
}

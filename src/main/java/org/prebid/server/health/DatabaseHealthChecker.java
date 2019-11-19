package org.prebid.server.health;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import org.prebid.server.health.model.Status;
import org.prebid.server.health.model.StatusResponse;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

public class DatabaseHealthChecker extends PeriodicHealthChecker {

    private static final String NAME = "database";

    private final JDBCClient jdbcClient;

    private StatusResponse status;

    public DatabaseHealthChecker(Vertx vertx, JDBCClient jdbcClient, long refreshPeriod) {
        super(vertx, refreshPeriod);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public StatusResponse status() {
        return status;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    void updateStatus() {
        final Promise<SQLConnection> connectionPromise = Promise.promise();
        jdbcClient.getConnection(connectionPromise);
        connectionPromise.future().setHandler(result ->
                status = StatusResponse.of(
                        result.succeeded() ? Status.UP.name() : Status.DOWN.name(),
                        ZonedDateTime.now(Clock.systemUTC())));
    }
}

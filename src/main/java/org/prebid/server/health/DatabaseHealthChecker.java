package org.prebid.server.health;

import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.prebid.server.health.model.Status;
import org.prebid.server.health.model.StatusResponse;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

public class DatabaseHealthChecker extends PeriodicHealthChecker {

    private static final String NAME = "database";

    private final Pool pool;

    private StatusResponse status;

    public DatabaseHealthChecker(Vertx vertx, Pool pool, long refreshPeriod) {
        super(vertx, refreshPeriod);
        this.pool = Objects.requireNonNull(pool);
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
        pool.getConnection().onComplete(result ->
                status = StatusResponse.of(
                        result.succeeded() ? Status.UP.name() : Status.DOWN.name(),
                        ZonedDateTime.now(Clock.systemUTC())));
    }
}

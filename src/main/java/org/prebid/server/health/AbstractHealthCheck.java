package org.prebid.server.health;

import io.vertx.core.Vertx;

import java.util.Objects;

public abstract class AbstractHealthCheck implements HealthChecker {

    private final Vertx vertx;
    private final long refreshPeriod;

    AbstractHealthCheck(Vertx vertx, long refreshPeriod) {
        this.vertx = Objects.requireNonNull(vertx);
        this.refreshPeriod = verifyRefreshPeriod(refreshPeriod);
    }

    @Override
    public void initialize() {
        checkStatus();
        vertx.setPeriodic(refreshPeriod, aLong -> checkStatus());
    }

    abstract void checkStatus();

    private static long verifyRefreshPeriod(long refreshPeriod) {
        if (refreshPeriod < 1) {
            throw new IllegalArgumentException("Refresh period for updating status be positive value");
        }
        return refreshPeriod;
    }
}

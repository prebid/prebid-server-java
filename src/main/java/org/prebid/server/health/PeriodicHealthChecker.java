package org.prebid.server.health;

import io.vertx.core.Vertx;

import java.util.Objects;

public abstract class PeriodicHealthChecker implements HealthChecker {

    private final Vertx vertx;
    private final long refreshPeriod;

    PeriodicHealthChecker(Vertx vertx, long refreshPeriod) {
        this.vertx = Objects.requireNonNull(vertx);
        this.refreshPeriod = verifyRefreshPeriod(refreshPeriod);
    }

    public void initialize() {
        updateStatus();
        vertx.setPeriodic(refreshPeriod, aLong -> updateStatus());
    }

    abstract void updateStatus();

    private static long verifyRefreshPeriod(long refreshPeriod) {
        if (refreshPeriod < 1) {
            throw new IllegalArgumentException("Refresh period for updating status be positive value");
        }
        return refreshPeriod;
    }
}

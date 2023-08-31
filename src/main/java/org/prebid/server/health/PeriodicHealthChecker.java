package org.prebid.server.health;

import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public abstract class PeriodicHealthChecker implements HealthChecker {

    private final Vertx vertx;
    private final long refreshPeriod;
    private final long jitter;

    PeriodicHealthChecker(Vertx vertx, long refreshPeriod, long jitter) {
        this.vertx = Objects.requireNonNull(vertx);
        this.refreshPeriod = verifyRefreshPeriod(refreshPeriod);
        this.jitter = verifyRefreshPeriodJitter(refreshPeriod, jitter);
    }

    public void initialize() {
        updateStatus();
        if (jitter == 0) {
            vertx.setPeriodic(refreshPeriod, aLong -> updateStatus());
        } else {
            setTimerWithJitter(vertx, refreshPeriod, jitter);
        }
    }

    abstract void updateStatus();

    private static long verifyRefreshPeriod(long refreshPeriod) {
        if (refreshPeriod < 1) {
            throw new IllegalArgumentException("Refresh period for updating status be positive value");
        }
        return refreshPeriod;
    }

    private static long verifyRefreshPeriodJitter(long refreshPeriod, long jitter) {
        if (jitter < 0 || jitter > refreshPeriod) {
            throw new IllegalArgumentException(
                    "Refresh period jitter for updating status be positive value and less than refresh period");
        }
        return jitter;
    }

    private void setTimerWithJitter(Vertx vertx, long delay, long jitter) {
        final long nextDelay = delay + ThreadLocalRandom.current().nextLong(jitter * -1, jitter);
        vertx.setTimer(delay, parameter -> {
            updateStatus();
            setTimerWithJitter(vertx, nextDelay, jitter);
        });
    }
}

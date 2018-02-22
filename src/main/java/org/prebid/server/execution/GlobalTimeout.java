package org.prebid.server.execution;

import java.time.Clock;

/**
 * Represents single timeout value that affects multiple operations.
 */
public class GlobalTimeout {

    private static final Clock CLOCK = Clock.systemDefaultZone();

    private final long deadline;

    private GlobalTimeout(long deadline) {
        this.deadline = deadline;
    }

    /**
     * Returns a {@link GlobalTimeout} instance expiring after specified amount of milliseconds starting from the
     * provided instant.
     */
    public static GlobalTimeout create(long startTime, long timeout) {
        if (startTime < 1 || timeout < 1) {
            throw new IllegalArgumentException("Start time and timeout must be positive");
        }

        return new GlobalTimeout(startTime + timeout);
    }

    /**
     * Returns a {@link GlobalTimeout} instance expiring after specified amount of milliseconds starting from the
     * current moment.
     */
    public static GlobalTimeout create(long timeout) {
        return create(CLOCK.millis(), timeout);
    }

    /**
     * Returns a {@link GlobalTimeout} instance expiring sooner than the current instance by specified amount of
     * milliseconds.
     */
    public GlobalTimeout minus(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }

        return new GlobalTimeout(deadline - amount);
    }

    /**
     * Returns amount of time remaining before this {@link GlobalTimeout} expires.
     */
    public long remaining() {
        return Math.max(deadline - CLOCK.millis(), 0);
    }
}

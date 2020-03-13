package org.prebid.server.execution;

import java.time.Clock;

/**
 * Represents single timeout value that affects multiple operations.
 */
public class Timeout {

    private final Clock clock;

    private final long deadline;

    Timeout(Clock clock, long deadline) {
        this.clock = clock;
        this.deadline = deadline;
    }

    /**
     * Returns a {@link Timeout} instance expiring sooner than the current instance by specified amount of
     * milliseconds.
     */
    public Timeout minus(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }

        return new Timeout(clock, deadline - amount);
    }

    /**
     * Returns amount of time remaining before this {@link Timeout} expires.
     */
    public long remaining() {
        return Math.max(deadline - clock.millis(), 0);
    }
}

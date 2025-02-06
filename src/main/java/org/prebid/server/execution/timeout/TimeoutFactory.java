package org.prebid.server.execution.timeout;

import java.time.Clock;

/**
 * Represents single timeout value that affects multiple operations.
 */
public class TimeoutFactory {

    private final Clock clock;

    public TimeoutFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns a {@link Timeout} instance expiring after specified amount of milliseconds starting from the
     * provided instant.
     */
    public Timeout create(long startTime, long timeout) {
        if (startTime < 1 || timeout < 1) {
            throw new IllegalArgumentException("Start time and timeout must be positive");
        }

        return new Timeout(clock, startTime + timeout);
    }

    /**
     * Returns a {@link Timeout} instance expiring after specified amount of milliseconds starting from the
     * current moment.
     */
    public Timeout create(long timeout) {
        return create(clock.millis(), timeout);
    }
}

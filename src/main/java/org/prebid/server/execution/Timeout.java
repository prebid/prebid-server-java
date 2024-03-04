package org.prebid.server.execution;

import lombok.Getter;

import java.time.Clock;

/**
 * Represents single timeout value that affects multiple operations.
 */
public class Timeout {

    private final Clock clock;

    @Getter
    private final long deadline;

    Timeout(Clock clock, long deadline) {
        this.clock = clock;
        this.deadline = deadline;
    }

    /**
     * Returns amount of time remaining before this {@link Timeout} expires.
     */
    public long remaining() {
        return Math.max(deadline - clock.millis(), 0);
    }
}

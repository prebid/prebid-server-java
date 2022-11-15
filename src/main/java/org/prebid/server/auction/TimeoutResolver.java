package org.prebid.server.auction;

public class TimeoutResolver {

    private final long minTimeout;
    private final long maxTimeout;
    private final long timeoutAdjustment;

    public TimeoutResolver(long minTimeout, long maxTimeout, long timeoutAdjustment) {
        validateTimeouts(minTimeout, maxTimeout);

        this.minTimeout = minTimeout;
        this.maxTimeout = maxTimeout;
        this.timeoutAdjustment = timeoutAdjustment;
    }

    private static void validateTimeouts(long minTimeout, long maxTimeout) {
        if (minTimeout <= 0 || maxTimeout <= 0) {
            throw new IllegalArgumentException(
                    "Both min and max timeouts should be grater than 0: min=%d, max=%d"
                            .formatted(minTimeout, maxTimeout));
        } else if (maxTimeout < minTimeout) {
            throw new IllegalArgumentException(
                    "Max timeout cannot be less than min timeout: min=%d, max=%d"
                            .formatted(minTimeout, maxTimeout));
        }
    }

    public long resolve(Long requestTimeout) {
        return requestTimeout == null
                ? maxTimeout
                : Math.max(Math.min(requestTimeout, maxTimeout), minTimeout);
    }

    public long adjustTimeout(long requestTimeout) {
        return requestTimeout == minTimeout || requestTimeout == maxTimeout || requestTimeout <= timeoutAdjustment
                ? requestTimeout
                : requestTimeout - timeoutAdjustment;
    }
}

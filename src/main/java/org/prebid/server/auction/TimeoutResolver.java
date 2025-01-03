package org.prebid.server.auction;

public class TimeoutResolver {

    private final long minTimeout;
    private final long maxTimeout;
    private final long upstreamResponseTime;

    public TimeoutResolver(long minTimeout, long maxTimeout, long upstreamResponseTime) {
        validateTimeouts(minTimeout, maxTimeout);

        this.minTimeout = minTimeout;
        this.maxTimeout = maxTimeout;
        this.upstreamResponseTime = upstreamResponseTime;
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

    public long limitToMax(Long timeout) {
        return timeout == null
                ? maxTimeout
                : Math.min(timeout, maxTimeout);
    }

    public long adjustForBidder(long timeout, int adjustFactor, long spentTime, long bidderTmaxDeductionMs) {
        return adjustWithFactor(timeout, adjustFactor / 100.0, spentTime, bidderTmaxDeductionMs);
    }

    public long adjustForRequest(long timeout, long spentTime) {
        return adjustWithFactor(timeout, 1.0, spentTime, 0L);
    }

    private long adjustWithFactor(long timeout, double adjustFactor, long spentTime, long deductionTime) {
        return limitToMin((long) (timeout * adjustFactor) - spentTime - deductionTime - upstreamResponseTime);
    }

    private long limitToMin(long timeout) {
        return Math.max(timeout, minTimeout);
    }
}

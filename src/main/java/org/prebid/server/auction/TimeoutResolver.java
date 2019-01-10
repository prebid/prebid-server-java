package org.prebid.server.auction;

/**
 * Component for processing timeout related functionality.
 */
class TimeoutResolver {

    private final long defaultTimeout;
    private final long maxTimeout;
    private final long timeoutAdjustment;

    TimeoutResolver(long defaultTimeout, long maxTimeout, long timeoutAdjustment) {
        if (maxTimeout < defaultTimeout) {
            throw new IllegalArgumentException(
                    String.format("Max timeout cannot be less than default timeout: max=%d, default=%d", maxTimeout,
                            defaultTimeout));
        }

        this.defaultTimeout = defaultTimeout;
        this.maxTimeout = maxTimeout;
        this.timeoutAdjustment = timeoutAdjustment;
    }

    /**
     * Resolves timeout according to given in request and pre-configured values (default, max, adjustment).
     */
    long resolve(Long requestTimeout) {
        final long result;

        if (requestTimeout == null) {
            result = defaultTimeout;
        } else if (requestTimeout > maxTimeout) {
            result = maxTimeout;
        } else if (timeoutAdjustment > 0) {
            result = requestTimeout - timeoutAdjustment; // negative value should be checked by caller
        } else {
            result = requestTimeout;
        }

        return result;
    }
}

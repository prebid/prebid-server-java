package org.prebid.server.auction;

/**
 * Component for processing timeout related functionality.
 */
public class TimeoutResolver {

    private final long defaultTimeout;
    private final long maxTimeout;
    private final long timeoutAdjustment;

    public TimeoutResolver(long defaultTimeout, long maxTimeout, long timeoutAdjustment) {
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
     * Resolves timeout according to given in request and pre-configured default and max values.
     */
    public long resolve(Long requestTimeout) {
        final long result;

        if (requestTimeout == null) {
            result = defaultTimeout;
        } else if (requestTimeout > maxTimeout) {
            result = maxTimeout;
        } else {
            result = requestTimeout;
        }

        return result;
    }

    /**
     * Determines timeout according to given in request and pre-configured adjustment value.
     */
    public long adjustTimeout(long requestTimeout) {
        final long result;

        if (requestTimeout == defaultTimeout || requestTimeout == maxTimeout) {
            result = requestTimeout;
        } else {
            result = requestTimeout > timeoutAdjustment ? requestTimeout - timeoutAdjustment : requestTimeout;
        }

        return result;
    }
}

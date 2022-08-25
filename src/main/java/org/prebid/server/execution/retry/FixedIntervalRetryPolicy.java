package org.prebid.server.execution.retry;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class FixedIntervalRetryPolicy implements MakeRetryPolicy {

    long delay;

    Integer retriesLeft;

    public static FixedIntervalRetryPolicy withLimit(long delay, int retryLimit) {
        return new FixedIntervalRetryPolicy(delay, retryLimit);
    }

    public static FixedIntervalRetryPolicy of(long delay) {
        return new FixedIntervalRetryPolicy(delay, null);
    }

    @Override
    public RetryPolicy next() {
        if (retriesLeft == null) {
            return this;
        }

        return retriesLeft - 1 > 0
                ? withLimit(delay, retriesLeft - 1)
                : NoRetryPolicy.instance();
    }
}


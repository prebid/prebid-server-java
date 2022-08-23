package org.prebid.server.execution.retry;

import lombok.Value;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class FixedIntervalRetryPolicy implements MakeRetryPolicy {

    long delay;

    int retriesLeft;

    @Override
    public RetryPolicy next() {
        return retriesLeft - 1 > 0
                ? of(delay, retriesLeft - 1)
                : NoRetryPolicy.instance();
    }
}


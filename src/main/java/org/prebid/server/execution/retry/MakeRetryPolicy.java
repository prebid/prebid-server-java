package org.prebid.server.execution.retry;

public sealed interface MakeRetryPolicy
        extends RetryPolicy
        permits ExponentialBackoffRetryPolicy, FixedIntervalRetryPolicy {

    long delay();

    RetryPolicy next();
}

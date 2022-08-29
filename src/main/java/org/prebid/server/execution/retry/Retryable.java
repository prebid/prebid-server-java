package org.prebid.server.execution.retry;

public sealed interface Retryable
        extends RetryPolicy
        permits ExponentialBackoffRetryPolicy, FixedIntervalRetryPolicy {

    long delay();

    RetryPolicy next();
}

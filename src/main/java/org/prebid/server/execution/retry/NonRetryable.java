package org.prebid.server.execution.retry;

public final class NonRetryable implements RetryPolicy {

    private static final NonRetryable INSTANCE = new NonRetryable();

    private NonRetryable() {
    }

    public static NonRetryable instance() {
        return INSTANCE;
    }
}

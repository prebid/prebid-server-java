package org.prebid.server.execution.retry;

public final class NoRetryPolicy implements RetryPolicy {

    private static final NoRetryPolicy INSTANCE = new NoRetryPolicy();

    private NoRetryPolicy() {
    }

    public static NoRetryPolicy instance() {
        return INSTANCE;
    }
}

package org.prebid.server.execution.retry;

public sealed interface RetryPolicy permits Retryable, NonRetryable {
}

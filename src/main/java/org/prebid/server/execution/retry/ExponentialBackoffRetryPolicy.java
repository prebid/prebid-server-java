package org.prebid.server.execution.retry;

import lombok.Value;
import lombok.experimental.Accessors;

import java.util.concurrent.ThreadLocalRandom;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class ExponentialBackoffRetryPolicy implements Retryable {

    long delay;

    long maxDelayMillis;

    double factor;

    double jitter;

    @Override
    public RetryPolicy next() {
        final long nextDelay = (long) Math.min(delay * factor, maxDelayMillis);
        final long variedDelay = Math.min(
                nextDelay + (long) ThreadLocalRandom.current().nextDouble(nextDelay * jitter), maxDelayMillis);
        return of(variedDelay, maxDelayMillis, factor, jitter);
    }
}

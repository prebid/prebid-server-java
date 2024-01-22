package org.prebid.server.privacy.gdpr.vendorlist;

import lombok.Value;
import org.prebid.server.execution.retry.RetryPolicy;
import org.prebid.server.execution.retry.Retryable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/*
 * This class is a dirty hack, that will be removed with Vertx update to 4.0
 * TODO: Replace with Vertx 4.0 Circuit breaker's backoff.
 */
public class VendorListFetchThrottler {

    private final Map<Integer, FetchAttempt> versionToFetchAttempt;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public VendorListFetchThrottler(RetryPolicy retryPolicy, Clock clock) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.clock = Objects.requireNonNull(clock);

        versionToFetchAttempt = new ConcurrentHashMap<>();
    }

    public boolean registerFetchAttempt(int version) {
        final Instant now = clock.instant();
        final FetchAttempt computedAttempt = versionToFetchAttempt.compute(
                version, (ignored, previousAttempt) -> resolveAttempt(previousAttempt, now));

        // Memory address of object returned by Instant.now() is used as unique identifier of attempt.
        // If memory address of computed `computedAttempt.attemptedAt` is equal to the `now` that we provided for
        // resolving, then it is our attempt, and we can fetch vendor list.
        return computedAttempt.attemptedAt == now;
    }

    private FetchAttempt resolveAttempt(FetchAttempt previousAttempt, Instant currentAttemptStart) {
        if (previousAttempt == null) {
            return FetchAttempt.of(retryPolicy, currentAttemptStart);
        }

        if (previousAttempt.retryPolicy instanceof Retryable previousAttemptRetryPolicy) {
            final Instant previouslyDecidedToRetryAfter = previousAttempt.attemptedAt.plus(
                    Duration.ofMillis(previousAttemptRetryPolicy.delay()));

            return previouslyDecidedToRetryAfter.isBefore(currentAttemptStart)
                    ? FetchAttempt.of(previousAttemptRetryPolicy.next(), currentAttemptStart)
                    : previousAttempt;
        }

        return previousAttempt;
    }

    public void succeedFetchAttempt(int version) {
        versionToFetchAttempt.remove(version);
    }

    @Value(staticConstructor = "of")
    private static class FetchAttempt {

        RetryPolicy retryPolicy;

        Instant attemptedAt;
    }
}

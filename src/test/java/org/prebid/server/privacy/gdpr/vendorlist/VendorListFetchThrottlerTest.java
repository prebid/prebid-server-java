package org.prebid.server.privacy.gdpr.vendorlist;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.execution.retry.ExponentialBackoffRetryPolicy;
import org.prebid.server.execution.retry.NonRetryable;
import org.prebid.server.execution.retry.RetryPolicy;

import java.time.Clock;
import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

public class VendorListFetchThrottlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Clock clock;

    @Test
    public void registerFetchAttemptShouldReturnTrueOnFirstAttemptForVersion() {
        // given
        final RetryPolicy retryPolicy = givenExponentialBackoffRetryPolicy(60000);
        final VendorListFetchThrottler target = new VendorListFetchThrottler(retryPolicy, clock);

        // when and then
        assertTrue(target.registerFetchAttempt(1));
    }

    @Test
    public void registerFetchAttemptShouldReturnFalseForAttemptIfDelayAfterPreviousAttemptForVersionHasNotPassed() {
        // given
        final RetryPolicy retryPolicy = givenExponentialBackoffRetryPolicy(Long.MAX_VALUE);
        final VendorListFetchThrottler target = new VendorListFetchThrottler(retryPolicy, clock);

        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:00:00Z"));
        target.registerFetchAttempt(1);
        // pretend that only one minute passed since last fetch attempt
        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:01:00Z"));

        // when and then
        assertFalse(target.registerFetchAttempt(1));
    }

    @Test
    public void registerFetchAttemptShouldReturnTrueForAttemptIfDelayAfterPreviousAttemptForVersionHasPassed() {
        // given
        final RetryPolicy retryPolicy = givenExponentialBackoffRetryPolicy(60000L);
        final VendorListFetchThrottler target = new VendorListFetchThrottler(retryPolicy, clock);

        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:00:00Z"));
        target.registerFetchAttempt(1);
        // pretend that only one minute passed since last fetch attempt
        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:02:00Z"));

        // when and then
        assertTrue(target.registerFetchAttempt(1));
    }

    @Test
    public void registerFetchAttemptShouldReturnFalseForAttemptWhenNoRetriesLeftForVersion() {
        // given
        final RetryPolicy retryPolicy = NonRetryable.instance();
        final VendorListFetchThrottler target = new VendorListFetchThrottler(retryPolicy, clock);

        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:00:00Z"));
        target.registerFetchAttempt(1);
        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:01:00Z"));

        // when and then
        assertFalse(target.registerFetchAttempt(1));
    }

    @Test
    public void succeedFetchAttemptShouldClearFetchAttemptForVersion() {
        // given
        final RetryPolicy retryPolicy = givenExponentialBackoffRetryPolicy(Long.MAX_VALUE);
        final VendorListFetchThrottler target = new VendorListFetchThrottler(retryPolicy, clock);

        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:00:00Z"));
        target.registerFetchAttempt(1);
        target.succeedFetchAttempt(1);

        given(clock.instant()).willReturn(Instant.parse("2023-01-22T13:01:00Z"));

        // when and then
        assertTrue(target.registerFetchAttempt(1));
    }

    private RetryPolicy givenExponentialBackoffRetryPolicy(long delay) {
        return ExponentialBackoffRetryPolicy.of(delay, Long.MAX_VALUE, 1, 0.1);
    }
}

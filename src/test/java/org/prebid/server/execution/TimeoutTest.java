package org.prebid.server.execution;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeoutTest {

    @Test
    public void minusShouldReturnTimeoutReducedBySpecifiedAmount() {
        // given
        final Instant now = Instant.now();
        final Timeout timeout =
                new Timeout(Clock.fixed(now, ZoneId.systemDefault()), now.plusMillis(1000L).toEpochMilli());

        // when and then
        assertThat(timeout.minus(500L).remaining()).isEqualTo(500L);
    }

    @Test
    public void remainingShouldReturnZeroIfTimeoutAlreadyExpired() {
        // given
        final Instant now = Instant.now();
        final Timeout timeout = new Timeout(Clock.fixed(now.plusMillis(500L), ZoneId.systemDefault()),
                now.toEpochMilli());

        // when and then
        assertThat(timeout.remaining()).isZero();
    }
}

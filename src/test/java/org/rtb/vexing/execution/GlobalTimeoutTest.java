package org.rtb.vexing.execution;

import org.junit.Test;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;

public class GlobalTimeoutTest {

    @Test
    public void createShouldFailOnNonPositiveStartTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> GlobalTimeout.create(0, 100));
    }

    @Test
    public void createShouldFailOnNonPositiveTimeout() {
        assertThatIllegalArgumentException().isThrownBy(() -> GlobalTimeout.create(100, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> GlobalTimeout.create(0));
    }

    @Test
    public void minusShouldFailOnNegativeAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> GlobalTimeout.create(500).minus(-1));
    }

    @Test
    public void createShouldReturnTimeoutStartedFromCurrentMoment() {
        // when
        final long remainingTimeout = GlobalTimeout.create(1000L).remaining();

        // then
        assertThat(remainingTimeout).isCloseTo(1000L, offset(20L));
    }

    @Test
    public void createShouldReturnTimeoutStartedFromSpecifiedMoment() {
        // when
        final long remainingTimeout = GlobalTimeout.create(Clock.systemDefaultZone().millis() - 500L, 1000L)
                .remaining();

        // then
        assertThat(remainingTimeout).isCloseTo(500L, offset(20L));
    }

    @Test
    public void minusShouldReturnTimeoutReducedBySpecifiedAmount() {
        // when
        final long remainingTimeout = GlobalTimeout.create(1000L).minus(500L).remaining();

        // then
        assertThat(remainingTimeout).isCloseTo(500L, offset(20L));
    }

    @Test
    public void remainingShouldReturnZeroIfTimeoutAlreadyExpired() {
        // when
        final long remainingTimeout = GlobalTimeout.create(Clock.systemDefaultZone().millis() - 1500L, 1000L)
                .remaining();

        // then
        assertThat(remainingTimeout).isZero();
    }

    @Test
    public void remainingShouldReturnReducedValueAsTimePasses() throws InterruptedException {
        // given
        final GlobalTimeout timeout = GlobalTimeout.create(1000L);

        // when
        TimeUnit.MILLISECONDS.sleep(100);
        final long remainingTimeout = timeout.remaining();

        // then
        assertThat(remainingTimeout).isCloseTo(900L, offset(20L));
    }
}
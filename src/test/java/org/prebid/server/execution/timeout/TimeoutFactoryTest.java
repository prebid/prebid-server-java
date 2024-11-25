package org.prebid.server.execution.timeout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TimeoutFactoryTest {

    private Clock clock;

    private TimeoutFactory timeoutFactory;

    @BeforeEach
    public void setUp() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeoutFactory = new TimeoutFactory(clock);
    }

    @Test
    public void createShouldFailOnNonPositiveStartTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> timeoutFactory.create(0, 100));
    }

    @Test
    public void createShouldFailOnNonPositiveTimeout() {
        assertThatIllegalArgumentException().isThrownBy(() -> timeoutFactory.create(100, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> timeoutFactory.create(0));
    }

    @Test
    public void createShouldReturnTimeoutStartedFromCurrentMoment() {
        // when
        final Timeout timeout = timeoutFactory.create(1000L);

        // then
        assertThat(timeout.remaining()).isEqualTo(1000L);
    }

    @Test
    public void createShouldReturnTimeoutStartedFromSpecifiedMoment() {
        // when
        final Timeout timeout = timeoutFactory.create(clock.instant().minusMillis(500L).toEpochMilli(), 1000L);

        // then
        assertThat(timeout.remaining()).isEqualTo(500L);
    }
}

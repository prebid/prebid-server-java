package org.prebid.server.auction;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TimeoutResolverTest {

    private static final long MIN_TIMEOUT = 100L;
    private static final long MAX_TIMEOUT = 200L;

    private TimeoutResolver timeoutResolver;

    @Before
    public void setUp() {
        timeoutResolver = new TimeoutResolver(MIN_TIMEOUT, MAX_TIMEOUT, 10L);
    }

    @Test
    public void creationShouldFailIfMinTimeoutEqualOrLassThanZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeoutResolver(0L, 1L, 0L))
                .withMessage("Both min and max timeouts should be grater than 0: min=0, max=1");
    }

    @Test
    public void creationShouldFailIfMaxTimeoutEqualsOrLassThanZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeoutResolver(1L, 0L, 0L))
                .withMessage("Both min and max timeouts should be grater than 0: min=1, max=0");
    }

    @Test
    public void creationShouldFailIfMaxTimeoutLessThanMin() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeoutResolver(2L, 1L, 0L))
                .withMessage("Max timeout cannot be less than min timeout: min=2, max=1");
    }

    @Test
    public void resolveShouldReturnExpectedTimeout() {
        assertThat(timeoutResolver.resolve(142L)).isEqualTo(142L);
    }

    @Test
    public void resolveShouldReturnMaxTimeoutAsDefault() {
        assertThat(timeoutResolver.resolve(null)).isEqualTo(MAX_TIMEOUT);
    }

    @Test
    public void resolveShouldReturnMaxTimeout() {
        assertThat(timeoutResolver.resolve(300L)).isEqualTo(MAX_TIMEOUT);
    }

    @Test
    public void resolveShouldReturnMinTimeout() {
        assertThat(timeoutResolver.resolve(50L)).isEqualTo(MIN_TIMEOUT);
    }

    @Test
    public void adjustTimeoutShouldReturnExpectedTimeout() {
        assertThat(timeoutResolver.adjustTimeout(142L)).isEqualTo(132L);
    }

    @Test
    public void adjustTimeoutShouldReturnMinTimeout() {
        assertThat(timeoutResolver.adjustTimeout(MIN_TIMEOUT)).isEqualTo(MIN_TIMEOUT);
    }

    @Test
    public void adjustTimeoutShouldReturnMaxTimeout() {
        assertThat(timeoutResolver.adjustTimeout(MAX_TIMEOUT)).isEqualTo(MAX_TIMEOUT);
    }
}

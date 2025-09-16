package org.prebid.server.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TimeoutResolverTest {

    private static final long MIN_TIMEOUT = 100L;
    private static final long MAX_TIMEOUT = 200L;

    private TimeoutResolver timeoutResolver;

    @BeforeEach
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
    public void limitToMaxShouldReturnExpectedTimeout() {
        assertThat(timeoutResolver.limitToMax(142L)).isEqualTo(142L);
    }

    @Test
    public void limitToMaxShouldReturnMaxTimeoutAsDefault() {
        assertThat(timeoutResolver.limitToMax(null)).isEqualTo(MAX_TIMEOUT);
    }

    @Test
    public void limitToMaxShouldReturnMaxTimeout() {
        assertThat(timeoutResolver.limitToMax(300L)).isEqualTo(MAX_TIMEOUT);
    }

    @Test
    public void adjustForBidderShouldReturnExpectedResult() {
        assertThat(timeoutResolver.adjustForBidder(300L, 70, 10L, 50L)).isEqualTo(140L);
    }

    @Test
    public void adjustForBidderShouldReturnMinTimeout() {
        assertThat(timeoutResolver.adjustForBidder(200L, 50, 10L, 100L)).isEqualTo(MIN_TIMEOUT);
    }

    @Test
    public void adjustForRequestShouldReturnExpectedResult() {
        assertThat(timeoutResolver.adjustForRequest(200L, 10L)).isEqualTo(180L);
    }

    @Test
    public void adjustForRequestShouldReturnMinTimeout() {
        assertThat(timeoutResolver.adjustForRequest(80L, 10L)).isEqualTo(MIN_TIMEOUT);
    }
}

package org.prebid.server.auction;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TimeoutResolverTest {

    private static final long DEFAULT_TIMEOUT = 100L;
    private static final long MAX_TIMEOUT = 200L;

    private TimeoutResolver timeoutResolver;

    @Before
    public void setUp() {
        timeoutResolver = new TimeoutResolver(DEFAULT_TIMEOUT, MAX_TIMEOUT, 10L);
    }

    @Test
    public void creationShouldFailIfMaxTimeoutLessThanDefault() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeoutResolver(2L, 1L, 0L))
                .withMessage("Max timeout cannot be less than default timeout: max=1, default=2");
    }

    @Test
    public void resolveShouldReturnExpectedTimeout() {
        assertThat(timeoutResolver.resolve(42L)).isEqualTo(42L);
    }

    @Test
    public void resolveShouldReturnDefaultTimeout() {
        assertThat(timeoutResolver.resolve(null)).isEqualTo(DEFAULT_TIMEOUT);
    }

    @Test
    public void resolveShouldReturnMaxTimeout() {
        assertThat(timeoutResolver.resolve(300L)).isEqualTo(MAX_TIMEOUT);
    }

    @Test
    public void adjustTimeoutShouldReturnExpectedTimeout() {
        assertThat(timeoutResolver.adjustTimeout(42L)).isEqualTo(32L);
    }

    @Test
    public void adjustTimeoutShouldReturnDefaultTimeout() {
        assertThat(timeoutResolver.adjustTimeout(DEFAULT_TIMEOUT)).isEqualTo(DEFAULT_TIMEOUT);
    }

    @Test
    public void adjustTimeoutShouldReturnMaxTimeout() {
        assertThat(timeoutResolver.adjustTimeout(MAX_TIMEOUT)).isEqualTo(MAX_TIMEOUT);
    }
}

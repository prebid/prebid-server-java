package org.prebid.server.auction;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TimeoutResolverTest {

    private TimeoutResolver timeoutResolver;

    @Before
    public void setUp() {
        timeoutResolver = new TimeoutResolver(100L, 200L, 10L);
    }

    @Test
    public void creationShouldFailIfMaxTimeoutLessThanDefault() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new TimeoutResolver(2L, 1L, 0L))
                .withMessage("Max timeout cannot be less than default timeout: max=1, default=2");
    }

    @Test
    public void shouldReturnExpectedTimeout() {
        assertThat(timeoutResolver.resolve(42L)).isEqualTo(32L);
    }

    @Test
    public void shouldReturnDefaultTimeout() {
        assertThat(timeoutResolver.resolve(null)).isEqualTo(100L);
    }

    @Test
    public void shouldReturnMaxTimeout() {
        assertThat(timeoutResolver.resolve(300L)).isEqualTo(200L);
    }

    @Test
    public void shouldReturnNegativeTimeout() {
        // Timeout resolver should not care of determined value,
        // this must be covered by caller/validator.
        assertThat(timeoutResolver.resolve(2L)).isEqualTo(-8L);
    }
}

package org.prebid.server.metric.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountMetricsVerbosityLevelTest {

    @Test
    public void isAtLeastShouldReturnFalse() {
        assertThat(AccountMetricsVerbosityLevel.NONE.isAtLeast(AccountMetricsVerbosityLevel.BASIC)).isFalse();
        assertThat(AccountMetricsVerbosityLevel.NONE.isAtLeast(AccountMetricsVerbosityLevel.DETAILED)).isFalse();
        assertThat(AccountMetricsVerbosityLevel.BASIC.isAtLeast(AccountMetricsVerbosityLevel.DETAILED)).isFalse();
    }

    @Test
    public void isAtLeastShouldReturnTrue() {
        assertThat(AccountMetricsVerbosityLevel.BASIC.isAtLeast(AccountMetricsVerbosityLevel.BASIC)).isTrue();
        assertThat(AccountMetricsVerbosityLevel.DETAILED.isAtLeast(AccountMetricsVerbosityLevel.BASIC)).isTrue();
        assertThat(AccountMetricsVerbosityLevel.DETAILED.isAtLeast(AccountMetricsVerbosityLevel.DETAILED)).isTrue();
    }
}

package org.prebid.server.metric.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountMetricsVerbosityLevelTest {

    @Test
    public void isAtLeastShouldReturnFalse() {
        assertThat(AccountMetricsVerbosityLevel.none.isAtLeast(AccountMetricsVerbosityLevel.basic)).isFalse();
        assertThat(AccountMetricsVerbosityLevel.none.isAtLeast(AccountMetricsVerbosityLevel.detailed)).isFalse();
        assertThat(AccountMetricsVerbosityLevel.basic.isAtLeast(AccountMetricsVerbosityLevel.detailed)).isFalse();
    }

    @Test
    public void isAtLeastShouldReturnTrue() {
        assertThat(AccountMetricsVerbosityLevel.basic.isAtLeast(AccountMetricsVerbosityLevel.basic)).isTrue();
        assertThat(AccountMetricsVerbosityLevel.detailed.isAtLeast(AccountMetricsVerbosityLevel.basic)).isTrue();
        assertThat(AccountMetricsVerbosityLevel.detailed.isAtLeast(AccountMetricsVerbosityLevel.detailed)).isTrue();
    }
}

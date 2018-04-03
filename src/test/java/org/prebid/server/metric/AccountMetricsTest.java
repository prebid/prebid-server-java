package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class AccountMetricsTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AccountMetrics(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AccountMetrics(new MetricRegistry(), null, null));
        assertThatNullPointerException().isThrownBy(() -> new AccountMetrics(new MetricRegistry(), CounterType.counter,
                null));
    }

    @Test
    public void forAdapterShouldReturnSameAdapterMetricsOnSuccessiveCalls() {
        // given
        final AccountMetrics accountMetrics = new AccountMetrics(new MetricRegistry(), CounterType.counter,
                "accountId");

        // when and then
        assertThat(accountMetrics.forAdapter("rubicon")).isSameAs(accountMetrics.forAdapter("rubicon"));
    }
}

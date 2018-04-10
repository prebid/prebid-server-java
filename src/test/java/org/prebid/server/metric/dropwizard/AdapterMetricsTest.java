package org.prebid.server.metric.dropwizard;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;
import org.prebid.server.metric.CounterType;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class AdapterMetricsTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AdapterMetrics(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AdapterMetrics(new MetricRegistry(), null, null));
        assertThatNullPointerException().isThrownBy(() -> new AdapterMetrics(new MetricRegistry(), CounterType.counter,
                null));

        assertThatNullPointerException().isThrownBy(() -> new AdapterMetrics(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AdapterMetrics(new MetricRegistry(), null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AdapterMetrics(new MetricRegistry(), CounterType.counter,
                null, null));
        assertThatNullPointerException().isThrownBy(() -> new AdapterMetrics(new MetricRegistry(), CounterType.counter,
                "accountId", null));
    }
}

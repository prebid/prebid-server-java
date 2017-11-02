package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class MetricsTest {

    private Metrics metrics;

    @Before
    public void setUp() {
        metrics = new Metrics(new MetricRegistry());
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new Metrics(null));
    }

    @Test
    public void counterForShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> metrics.counterFor(null));
    }

    @Test
    public void counterForShouldReturnSameCounterOnSuccessiveCalls() {
        assertThat(metrics.counterFor(MetricName.requests))
                .isNotNull()
                .isSameAs(metrics.counterFor(MetricName.requests));
    }
}

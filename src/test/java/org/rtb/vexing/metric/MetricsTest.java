package org.rtb.vexing.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.config.ApplicationConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class MetricsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationConfig config;

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> Metrics.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> Metrics.create(new MetricRegistry(), null));
    }

    @Test
    public void incCounterShouldFailOnNullArguments() {
        // given
        given(config.getString(eq("metrics.metricType"))).willReturn("counter");

        final Metrics metrics = Metrics.create(new MetricRegistry(), config);

        // when and then
        assertThatNullPointerException().isThrownBy(() -> metrics.incCount(null));
    }

    @Test
    public void incCounterShouldIncrementSameFlushingCounterOnSuccessiveCalls() {
        // given
        final MetricRegistry metricRegistry = new MetricRegistry();
        given(config.getString(eq("metrics.metricType"))).willReturn("flushingCounter");

        final Metrics metrics = Metrics.create(metricRegistry, config);

        // when
        metrics.incCount(MetricName.requests);
        metrics.incCount(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("requests"))
                .isNotNull()
                .returns(2L, Counter::getCount);
    }

    @Test
    public void flushingCounterShouldResetOnGetCount() {
        // given
        final MetricRegistry metricRegistry = new MetricRegistry();
        given(config.getString(eq("metrics.metricType"))).willReturn("flushingCounter");

        final Metrics metrics = Metrics.create(metricRegistry, config);

        // when
        metrics.incCount(MetricName.requests);
        metrics.incCount(MetricName.requests);
        final long count1 = metricRegistry.counter("requests").getCount();
        metrics.incCount(MetricName.requests);
        final long count2 = metricRegistry.counter("requests").getCount();
        final long count3 = metricRegistry.counter("requests").getCount();

        // then
        assertThat(count1).isEqualTo(2);
        assertThat(count2).isEqualTo(1);
        assertThat(count3).isZero();
    }

    @Test
    public void incCounterShouldIncrementSameCounterOnSuccessiveCalls() {
        // given
        final MetricRegistry metricRegistry = new MetricRegistry();
        given(config.getString(eq("metrics.metricType"))).willReturn("counter");

        final Metrics metrics = Metrics.create(metricRegistry, config);

        // when
        metrics.incCount(MetricName.requests);
        metrics.incCount(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("requests"))
                .isNotNull()
                .returns(2L, Counter::getCount);
    }

    @Test
    public void counterShouldNotResetOnGetCount() {
        // given
        final MetricRegistry metricRegistry = new MetricRegistry();
        given(config.getString(eq("metrics.metricType"))).willReturn("counter");

        final Metrics metrics = Metrics.create(metricRegistry, config);

        // when
        metrics.incCount(MetricName.requests);
        metrics.incCount(MetricName.requests);
        final long count1 = metricRegistry.counter("requests").getCount();
        metrics.incCount(MetricName.requests);
        final long count2 = metricRegistry.counter("requests").getCount();
        final long count3 = metricRegistry.counter("requests").getCount();

        // then
        assertThat(count1).isEqualTo(2);
        assertThat(count2).isEqualTo(3);
        assertThat(count3).isEqualTo(3);
    }

    @Test
    public void incCounterShouldMarkSameMeterOnSuccessiveCalls() {
        // given
        final MetricRegistry metricRegistry = new MetricRegistry();
        given(config.getString(eq("metrics.metricType"))).willReturn("meter");

        final Metrics metrics = Metrics.create(metricRegistry, config);

        // when
        metrics.incCount(MetricName.requests);
        metrics.incCount(MetricName.requests);

        // then
        assertThat(metricRegistry.meter("requests"))
                .isNotNull()
                .returns(2L, Meter::getCount);
    }

    @Test
    public void meterShouldNotResetOnGetCount() {
        // given
        final MetricRegistry metricRegistry = new MetricRegistry();
        given(config.getString(eq("metrics.metricType"))).willReturn("meter");

        final Metrics metrics = Metrics.create(metricRegistry, config);

        // when
        metrics.incCount(MetricName.requests);
        metrics.incCount(MetricName.requests);
        final long count1 = metricRegistry.meter("requests").getCount();
        metrics.incCount(MetricName.requests);
        final long count2 = metricRegistry.meter("requests").getCount();
        final long count3 = metricRegistry.meter("requests").getCount();

        // then
        assertThat(count1).isEqualTo(2);
        assertThat(count2).isEqualTo(3);
        assertThat(count3).isEqualTo(3);
    }
}

package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.MockClock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class UpdatableMetricsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private MeterRegistry meterRegistry;
    private UpdatableMetrics updatableMetrics;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        updatableMetrics = givenUpdatableMetricsWith();
    }

    @Test
    public void incCounterShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(meterRegistry, metricName -> "someprefix." + metricName);

        // when
        updatableMetrics.incCounter(MetricName.requests, 5);

        // then
        assertThat(meterRegistry.counter("someprefix.requests").count()).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void incCounterShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(meterRegistry, nameCreator);

        // when
        updatableMetrics.incCounter(MetricName.requests, 5);
        updatableMetrics.incCounter(MetricName.requests, 6);

        // then
        verify(nameCreator).apply(eq(MetricName.requests));
    }

    @Test
    public void incCounterShouldIncrementByOne() {
        // given
        updatableMetrics = new UpdatableMetrics(meterRegistry, MetricName::toString);

        // when
        updatableMetrics.incCounter(MetricName.requests);

        // then
        assertThat(meterRegistry.counter("requests").count()).isEqualTo(1);
    }

    @Test
    public void updateTimerShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(meterRegistry, metricName -> "someprefix." + metricName);

        // when
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);

        // then
        assertThat(meterRegistry.timer("someprefix.request_time").count()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void updateTimerShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(meterRegistry, nameCreator);

        // when
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);

        // then
        verify(nameCreator).apply(eq(MetricName.request_time));
    }

    @Test
    public void updateTimerShouldConvertToNanos() {
        // given
        updatableMetrics = new UpdatableMetrics(meterRegistry, MetricName::toString);

        // when
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);

        // then
        assertThat(meterRegistry.timer("request_time").takeSnapshot().total()).isEqualTo(1_000_000_000L);
    }

    @Test
    public void updateHistogramShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(meterRegistry, metricName -> "someprefix." + metricName);

        // when
        updatableMetrics.updateHistogram(MetricName.prices, 1000L);

        // then
        assertThat(meterRegistry.summary("someprefix.prices").count()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void updateHistogramShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(meterRegistry, nameCreator);

        // when
        updatableMetrics.updateHistogram(MetricName.prices, 1000L);
        updatableMetrics.updateHistogram(MetricName.prices, 1000L);

        // then
        verify(nameCreator).apply(eq(MetricName.prices));
    }

    @Test
    public void createGaugeShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(meterRegistry, metricName -> "someprefix." + metricName);

        // when
        updatableMetrics.createGauge(MetricName.opened, () -> 1);

        // then
        assertThat(meterRegistry.get("someprefix.opened").gauge().value()).isEqualTo(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void createGaugeShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(meterRegistry, nameCreator);

        // when
        updatableMetrics.createGauge(MetricName.opened, () -> 1);
        updatableMetrics.createGauge(MetricName.opened, () -> 1);

        // then
        verify(nameCreator).apply(eq(MetricName.opened));
    }

    @Test
    public void removeMetricShouldRemoveExistingMetric() {
        // given
        updatableMetrics = new UpdatableMetrics(meterRegistry, MetricName::toString);

        // when
        updatableMetrics.createGauge(MetricName.opened, () -> 1);
        updatableMetrics.removeMetric(MetricName.opened);

        // then
        assertThat(meterRegistry.find(MetricName.opened.toString()).meter()).isNull();
    }

    private UpdatableMetrics givenUpdatableMetricsWith() {
        return new UpdatableMetrics(meterRegistry, MetricName::toString);
    }
}

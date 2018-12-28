package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
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

    private MetricRegistry metricRegistry;
    private UpdatableMetrics updatableMetrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        updatableMetrics = givenUpdatableMetricsWith(CounterType.counter);
    }

    @Test
    public void incCounterShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter,
                metricName -> "someprefix." + metricName.toString());

        // when
        updatableMetrics.incCounter(MetricName.requests, 5);

        // then
        assertThat(metricRegistry.counter("someprefix.requests").getCount()).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void incCounterShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter, nameCreator);

        // when
        updatableMetrics.incCounter(MetricName.requests, 5);
        updatableMetrics.incCounter(MetricName.requests, 6);

        // then
        verify(nameCreator).apply(eq(MetricName.requests));
    }

    @Test
    public void incCounterShouldIncrementByOne() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter, MetricName::toString);

        // when
        updatableMetrics.incCounter(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateTimerShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter,
                metricName -> "someprefix." + metricName.toString());

        // when
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);

        // then
        assertThat(metricRegistry.timer("someprefix.request_time").getCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void updateTimerShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter, nameCreator);

        // when
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);

        // then
        verify(nameCreator).apply(eq(MetricName.request_time));
    }

    @Test
    public void updateTimerShouldConvertToNanos() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter, MetricName::toString);

        // when
        updatableMetrics.updateTimer(MetricName.request_time, 1000L);

        // then
        assertThat(metricRegistry.timer("request_time").getSnapshot().getValues()).containsOnly(1_000_000_000L);
    }

    @Test
    public void updateHistogramShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter,
                metricName -> "someprefix." + metricName.toString());

        // when
        updatableMetrics.updateHistogram(MetricName.prices, 1000L);

        // then
        assertThat(metricRegistry.histogram("someprefix.prices").getCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void updateHistogramShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter, nameCreator);

        // when
        updatableMetrics.updateHistogram(MetricName.prices, 1000L);
        updatableMetrics.updateHistogram(MetricName.prices, 1000L);

        // then
        verify(nameCreator).apply(eq(MetricName.prices));
    }

    private UpdatableMetrics givenUpdatableMetricsWith(CounterType counterType) {
        return new UpdatableMetrics(metricRegistry, counterType, MetricName::toString);
    }
}

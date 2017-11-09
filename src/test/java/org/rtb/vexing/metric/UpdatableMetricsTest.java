package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
    public void incCounterShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> updatableMetrics.incCounter(null));
    }

    @Test
    public void incCounterShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter,
                metricName -> "someprefix." + metricName.name());

        // when
        updatableMetrics.incCounter(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("someprefix.requests").getCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void incCounterShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter, nameCreator);

        // when
        updatableMetrics.incCounter(MetricName.requests);
        updatableMetrics.incCounter(MetricName.requests);

        // then
        verify(nameCreator, times(1)).apply(eq(MetricName.requests));
    }

    @Test
    public void updateTimerNanosShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> updatableMetrics.updateTimerNanos(null, 0L));
    }

    @Test
    public void updateTimerNanosShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter,
                metricName -> "someprefix." + metricName.name());

        // when
        updatableMetrics.updateTimerNanos(MetricName.request_time, 1000L);

        // then
        assertThat(metricRegistry.timer("someprefix.request_time").getCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void updateTimerNanosShouldCreateMetricNameOnlyOnceOnSuccessiveCalls() {
        // given
        final Function<MetricName, String> nameCreator = mock(Function.class);
        given(nameCreator.apply(any())).willReturn("");

        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter, nameCreator);

        // when
        updatableMetrics.updateTimerNanos(MetricName.request_time, 1000L);
        updatableMetrics.updateTimerNanos(MetricName.request_time, 1000L);

        // then
        verify(nameCreator, times(1)).apply(eq(MetricName.request_time));
    }

    @Test
    public void updateHistogramShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> updatableMetrics.updateHistogram(null, 0L));
    }

    @Test
    public void updateHistogramShouldCreateMetricNameUsingProvidedCreator() {
        // given
        updatableMetrics = new UpdatableMetrics(metricRegistry, CounterType.counter,
                metricName -> "someprefix." + metricName.name());

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
        verify(nameCreator, times(1)).apply(eq(MetricName.prices));
    }

    private UpdatableMetrics givenUpdatableMetricsWith(CounterType counterType) {
        return new UpdatableMetrics(metricRegistry, counterType, Enum::name);
    }
}

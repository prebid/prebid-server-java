package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.graphite.GraphiteReporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.spring.config.MetricsProperties;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;

public class ReporterFactoryTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private MetricRegistry metricRegistry;
    @Mock
    private MetricsProperties properties;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> ReporterFactory.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> ReporterFactory.create(metricRegistry, null));
    }

    @Test
    public void createShouldReturnEmptyIfTypeIsMissingInConfig() {
        assertThat(ReporterFactory.create(metricRegistry, properties)).isEmpty();
    }

    @Test
    public void createShouldFailWhenRequiredPropertiesNull() {
        given(properties.getType()).willReturn("graphite");

        assertThatNullPointerException().isThrownBy(() ->
                ReporterFactory.create(metricRegistry, properties));
    }

    @Test
    public void createShouldReturnGraphiteReporter() {
        // given
        given(properties.getType()).willReturn("graphite");
        given(properties.getHost()).willReturn("localhost:20003");
        given(properties.getPrefix()).willReturn("someprefix");
        given(properties.getInterval()).willReturn(5);

        // when
        final Optional<ScheduledReporter> reporter = ReporterFactory.create(metricRegistry, properties);

        // then
        assertThat(reporter).containsInstanceOf(GraphiteReporter.class);
    }
}

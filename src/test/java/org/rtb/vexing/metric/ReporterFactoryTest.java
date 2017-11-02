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
import org.rtb.vexing.config.ApplicationConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class ReporterFactoryTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private MetricRegistry metricRegistry;
    @Mock
    private ApplicationConfig config;

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
        assertThat(ReporterFactory.create(metricRegistry, config)).isEmpty();
    }

    @Test
    public void createShouldReturnGraphiteReporter() {
        // given
        given(config.getString(eq("metrics.type"), anyString())).willReturn("graphite");
        given(config.getString(eq("metrics.host"))).willReturn("localhost:20003");
        given(config.getString(eq("metrics.prefix"))).willReturn("someprefix");
        given(config.getInteger(eq("metrics.interval"))).willReturn(5);

        // when
        final Optional<ScheduledReporter> reporter = ReporterFactory.create(metricRegistry, config);

        // then
        assertThat(reporter).containsInstanceOf(GraphiteReporter.class);
    }
}

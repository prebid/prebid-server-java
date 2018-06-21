package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestMetricsTest {

    private MetricRegistry metricRegistry;

    private Metrics metrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        metrics = new Metrics(metricRegistry, CounterType.counter);
    }

    @Test
    public void forRequestTypeShouldReturnRequestMetricsConfiguredWithRequestType() {
        // when
        metrics.forRequestType(MetricName.openrtb2web).incCounter(MetricName.ok);

        // then
        assertThat(metricRegistry.counter("requests.ok.openrtb2-web").getCount()).isEqualTo(1);
    }
}
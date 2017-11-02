package org.rtb.vexing.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import java.util.Objects;

public class Metrics {

    private final MetricRegistry metricRegistry;

    public Metrics(MetricRegistry metricRegistry) {
        this.metricRegistry = Objects.requireNonNull(metricRegistry);
    }

    public Counter counterFor(MetricName name) {
        return metricRegistry.counter(Objects.requireNonNull(name).name());
    }
}

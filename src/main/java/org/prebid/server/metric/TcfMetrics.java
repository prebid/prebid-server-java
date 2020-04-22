package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Support for TCF metrics.
 */
class TcfMetrics extends UpdatableMetrics {

    TcfMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix)));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.tcf.%s", prefix, metricName.toString());
    }
}

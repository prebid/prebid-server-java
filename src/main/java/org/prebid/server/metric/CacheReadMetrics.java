package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

public class CacheReadMetrics extends UpdatableMetrics {

    CacheReadMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix)));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.read.%s".formatted(prefix, metricName);
    }

}

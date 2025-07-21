package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

public class CacheWriteMetrics extends UpdatableMetrics {

    CacheWriteMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix)));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.write.%s".formatted(prefix, metricName);
    }
}

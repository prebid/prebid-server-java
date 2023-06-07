package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

public class CacheCreativeSizeMetrics extends UpdatableMetrics {

    CacheCreativeSizeMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix)));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.creative_size.%s".formatted(prefix, metricName);
    }
}

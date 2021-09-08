package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

class HookSuccessMetrics extends UpdatableMetrics {

    HookSuccessMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    private static String createPrefix(String prefix) {
        return String.format("%s.success", prefix);
    }
}

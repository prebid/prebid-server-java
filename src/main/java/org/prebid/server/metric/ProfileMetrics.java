package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

class ProfileMetrics extends UpdatableMetrics {

    ProfileMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType), nameCreator());
    }

    ProfileMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix)));
    }

    private static Function<MetricName, String> nameCreator() {
        return "profiles.%s"::formatted;
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.profiles.%s".formatted(prefix, metricName);
    }
}

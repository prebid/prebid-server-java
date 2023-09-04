package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

class HookSuccessMetrics extends UpdatableMetrics {

    HookSuccessMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(meterRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix) {
        return prefix + ".success";
    }
}

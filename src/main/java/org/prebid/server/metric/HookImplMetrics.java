package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

class HookImplMetrics extends UpdatableMetrics {

    private final HookSuccessMetrics successMetrics;

    HookImplMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix, String hookImplCode) {
        super(
                Objects.requireNonNull(meterRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(hookImplCode))));

        successMetrics = new HookSuccessMetrics(meterRegistry, counterType, createPrefix(prefix, hookImplCode));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix, String stage) {
        return "%s.hook.%s".formatted(prefix, stage);
    }

    HookSuccessMetrics success() {
        return successMetrics;
    }
}

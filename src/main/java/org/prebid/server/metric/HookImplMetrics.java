package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

class HookImplMetrics extends UpdatableMetrics {

    private final HookSuccessMetrics successMetrics;

    HookImplMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, String hookImplCode) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(hookImplCode))));

        successMetrics = new HookSuccessMetrics(metricRegistry, counterType, createPrefix(prefix, hookImplCode));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    private static String createPrefix(String prefix, String stage) {
        return String.format("%s.hook.%s", prefix, stage);
    }

    HookSuccessMetrics success() {
        return successMetrics;
    }
}

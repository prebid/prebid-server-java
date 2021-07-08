package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

class HooksMetrics extends UpdatableMetrics {

    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Function<String, ModuleMetrics> moduleMetricsCreator;
    private final Map<String, ModuleMetrics> moduleMetrics;

    HooksMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        moduleMetricsCreator = moduleCode ->
                new ModuleMetrics(metricRegistry, counterType, createPrefix(prefix), moduleCode);
        moduleMetrics = new HashMap<>();
    }

    HooksMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix()));

        moduleMetricsCreator = moduleCode ->
                new ModuleMetrics(metricRegistry, counterType, createPrefix(), moduleCode);
        moduleMetrics = new HashMap<>();
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    private static String createPrefix(String prefix) {
        return String.format("%s.%s", prefix, createPrefix());
    }

    private static String createPrefix() {
        return "modules";
    }

    ModuleMetrics module(String moduleCode) {
        return moduleMetrics.computeIfAbsent(moduleCode, moduleMetricsCreator);
    }
}

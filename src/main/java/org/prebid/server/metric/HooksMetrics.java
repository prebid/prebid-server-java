package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

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

    HooksMetrics(MeterRegistry meterRegistry, String prefix) {
        super(
                Objects.requireNonNull(meterRegistry),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        moduleMetricsCreator = moduleCode ->
                new ModuleMetrics(meterRegistry, createPrefix(prefix), moduleCode);
        moduleMetrics = new HashMap<>();
    }

    HooksMetrics(MeterRegistry meterRegistry) {
        super(Objects.requireNonNull(meterRegistry),
                nameCreator(createPrefix()));

        moduleMetricsCreator = moduleCode ->
                new ModuleMetrics(meterRegistry, createPrefix(), moduleCode);
        moduleMetrics = new HashMap<>();
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix) {
        return "%s.%s".formatted(prefix, createPrefix());
    }

    private static String createPrefix() {
        return "modules";
    }

    ModuleMetrics module(String moduleCode) {
        return moduleMetrics.computeIfAbsent(moduleCode, moduleMetricsCreator);
    }
}

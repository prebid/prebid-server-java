package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;
import org.prebid.server.hooks.execution.model.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

class ModuleMetrics extends UpdatableMetrics {

    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Function<Stage, StageMetrics> stageMetricsCreator;
    private final Map<Stage, StageMetrics> stageMetrics;

    private final HookSuccessMetrics successMetrics;

    ModuleMetrics(MeterRegistry meterRegistry, String prefix, String moduleCode) {
        super(Objects.requireNonNull(meterRegistry),
                nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(moduleCode))));

        stageMetricsCreator = stage ->
                new StageMetrics(meterRegistry, createPrefix(prefix, moduleCode), stage);
        stageMetrics = new HashMap<>();

        successMetrics = new HookSuccessMetrics(meterRegistry, createPrefix(prefix, moduleCode));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix, String moduleCode) {
        return "%s.module.%s".formatted(prefix, moduleCode);
    }

    StageMetrics stage(Stage stage) {
        return stageMetrics.computeIfAbsent(stage, stageMetricsCreator);
    }

    HookSuccessMetrics success() {
        return successMetrics;
    }
}

package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
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

    ModuleMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, String moduleCode) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(moduleCode))));

        stageMetricsCreator = stage ->
                new StageMetrics(metricRegistry, counterType, createPrefix(prefix, moduleCode), stage);
        stageMetrics = new HashMap<>();

        successMetrics = new HookSuccessMetrics(metricRegistry, counterType, createPrefix(prefix, moduleCode));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    private static String createPrefix(String prefix, String moduleCode) {
        return String.format("%s.module.%s", prefix, moduleCode);
    }

    StageMetrics stage(Stage stage) {
        return stageMetrics.computeIfAbsent(stage, stageMetricsCreator);
    }

    HookSuccessMetrics success() {
        return successMetrics;
    }
}

package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Settings cache metrics support.
 */
class SettingsCacheMetrics extends UpdatableMetrics {

    private final Function<MetricName, RefreshSettingsCacheMetrics> refreshSettingsCacheMetricsCreator;
    private final Map<MetricName, RefreshSettingsCacheMetrics> refreshSettingsCacheMetrics;

    SettingsCacheMetrics(MetricRegistry metricRegistry, CounterType counterType, MetricName type) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(type))));

        refreshSettingsCacheMetricsCreator = refreshType ->
                new RefreshSettingsCacheMetrics(metricRegistry, counterType, createPrefix(type), refreshType);
        refreshSettingsCacheMetrics = new HashMap<>();
    }

    RefreshSettingsCacheMetrics forRefreshType(MetricName refreshType) {
        return refreshSettingsCacheMetrics.computeIfAbsent(refreshType, refreshSettingsCacheMetricsCreator);
    }

    private static String createPrefix(MetricName type) {
        return String.format("settings.cache.%s", type.toString());
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    static class RefreshSettingsCacheMetrics extends UpdatableMetrics {

        RefreshSettingsCacheMetrics(MetricRegistry metricRegistry,
                                    CounterType counterType,
                                    String prefix,
                                    MetricName type) {

            super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                    nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(type))));
        }

        private static String createPrefix(String prefix, MetricName type) {
            return String.format("%s.refresh.%s", prefix, type.toString());
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> String.format("%s.%s", prefix, metricName.toString());
        }
    }
}

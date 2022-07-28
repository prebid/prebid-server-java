package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

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

    SettingsCacheMetrics(MeterRegistry meterRegistry, MetricName type) {
        super(Objects.requireNonNull(meterRegistry),
                nameCreator(createPrefix(Objects.requireNonNull(type))));

        refreshSettingsCacheMetricsCreator = refreshType ->
                new RefreshSettingsCacheMetrics(meterRegistry, createPrefix(type), refreshType);
        refreshSettingsCacheMetrics = new HashMap<>();
    }

    RefreshSettingsCacheMetrics forRefreshType(MetricName refreshType) {
        return refreshSettingsCacheMetrics.computeIfAbsent(refreshType, refreshSettingsCacheMetricsCreator);
    }

    private static String createPrefix(MetricName type) {
        return "settings.cache." + type.toString();
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    static class RefreshSettingsCacheMetrics extends UpdatableMetrics {

        RefreshSettingsCacheMetrics(MeterRegistry meterRegistry,
                                    String prefix,
                                    MetricName type) {

            super(Objects.requireNonNull(meterRegistry),
                    nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(type))));
        }

        private static String createPrefix(String prefix, MetricName type) {
            return "%s.refresh.%s".formatted(prefix, type);
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> "%s.%s".formatted(prefix, metricName);
        }
    }
}

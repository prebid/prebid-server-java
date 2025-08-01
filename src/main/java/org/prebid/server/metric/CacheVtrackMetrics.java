package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

class CacheVtrackMetrics extends UpdatableMetrics {

    private final CacheReadMetrics readMetrics;
    private final CacheWriteMetrics writeMetrics;

    CacheVtrackMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        readMetrics = new CacheReadMetrics(metricRegistry, counterType, createPrefix(prefix));
        writeMetrics = new CacheWriteMetrics(metricRegistry, counterType, createPrefix(prefix));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix) {
        return prefix + ".vtrack";
    }

    CacheReadMetrics read() {
        return readMetrics;
    }

    CacheWriteMetrics write() {
        return writeMetrics;
    }

}

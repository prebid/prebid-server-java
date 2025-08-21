package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

class CacheModuleStorageMetrics extends UpdatableMetrics {

    private final CacheReadMetrics readMetrics;
    private final CacheWriteMetrics writeMetrics;
    private final CacheCreativeSizeMetrics creativeSizeMetrics;
    private final CacheCreativeTtlMetrics creativeTtlMetrics;

    CacheModuleStorageMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        readMetrics = new CacheReadMetrics(metricRegistry, counterType, createPrefix(prefix));
        writeMetrics = new CacheWriteMetrics(metricRegistry, counterType, createPrefix(prefix));
        creativeSizeMetrics = new CacheCreativeSizeMetrics(metricRegistry, counterType, createPrefix(prefix));
        creativeTtlMetrics = new CacheCreativeTtlMetrics(metricRegistry, counterType, createPrefix(prefix));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix) {
        return prefix + ".module_storage";
    }

    CacheReadMetrics read() {
        return readMetrics;
    }

    CacheWriteMetrics write() {
        return writeMetrics;
    }

    CacheCreativeSizeMetrics creativeSize() {
        return creativeSizeMetrics;
    }

    CacheCreativeTtlMetrics creativeTtl() {
        return creativeTtlMetrics;
    }

}

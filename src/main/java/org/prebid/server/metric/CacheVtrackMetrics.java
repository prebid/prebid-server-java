package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.model.CacheCreativeType;

import java.util.Objects;
import java.util.function.Function;

class CacheVtrackMetrics extends UpdatableMetrics {

    private final CacheReadMetrics readMetrics;
    private final CacheWriteMetrics writeMetrics;
    private final CacheCreativeSizeMetrics creativeSizeMetrics;
    private final CacheCreativeTtlMetrics creativeTtlMetrics;

    CacheVtrackMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        readMetrics = new CacheReadMetrics(metricRegistry, counterType, createPrefix(prefix));
        writeMetrics = new CacheWriteMetrics(metricRegistry, counterType, createPrefix(prefix));
        creativeSizeMetrics = new CacheCreativeSizeMetrics(
                metricRegistry, counterType, createPrefix(prefix), CacheCreativeType.CREATIVE);
        creativeTtlMetrics = new CacheCreativeTtlMetrics(
                metricRegistry, counterType, createPrefix(prefix), CacheCreativeType.CREATIVE);
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

    CacheCreativeSizeMetrics creativeSize() {
        return creativeSizeMetrics;
    }

    CacheCreativeTtlMetrics creativeTtl() {
        return creativeTtlMetrics;
    }

}

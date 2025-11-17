package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.model.CacheCreativeType;

import java.util.Objects;
import java.util.function.Function;

class CacheModuleStorageMetrics extends UpdatableMetrics {

    private final CacheReadMetrics readMetrics;
    private final CacheWriteMetrics writeMetrics;
    private final CacheCreativeSizeMetrics entrySizeMetrics;
    private final CacheCreativeTtlMetrics entryTtlMetrics;

    CacheModuleStorageMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, String module) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(module))));

        readMetrics = new CacheReadMetrics(metricRegistry, counterType, createPrefix(prefix, module));
        writeMetrics = new CacheWriteMetrics(metricRegistry, counterType, createPrefix(prefix, module));
        entrySizeMetrics = new CacheCreativeSizeMetrics(
                metricRegistry, counterType, createPrefix(prefix, module), CacheCreativeType.ENTRY);
        entryTtlMetrics = new CacheCreativeTtlMetrics(
                metricRegistry, counterType, createPrefix(prefix, module), CacheCreativeType.ENTRY);
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix, String moduleCode) {
        return "%s.module_storage.%s".formatted(prefix, moduleCode);
    }

    CacheReadMetrics read() {
        return readMetrics;
    }

    CacheWriteMetrics write() {
        return writeMetrics;
    }

    CacheCreativeSizeMetrics entrySize() {
        return entrySizeMetrics;
    }

    CacheCreativeTtlMetrics entryTtl() {
        return entryTtlMetrics;
    }

}

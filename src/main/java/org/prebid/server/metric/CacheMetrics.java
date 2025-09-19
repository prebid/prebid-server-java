package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.model.CacheCreativeType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Cache metrics support.
 */
class CacheMetrics extends UpdatableMetrics {

    private final RequestMetrics requestsMetrics;
    private final CacheCreativeSizeMetrics cacheCreativeSizeMetrics;
    private final CacheCreativeTtlMetrics cacheCreativeTtlMetrics;
    private final CacheVtrackMetrics cacheVtrackMetrics;
    private final Map<String, CacheModuleStorageMetrics> cacheModuleStorageMetrics;
    private final Function<String, CacheModuleStorageMetrics> cacheModuleStorageMetricsCreator;

    CacheMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix()));

        requestsMetrics = new RequestMetrics(metricRegistry, counterType, createPrefix());
        cacheCreativeSizeMetrics = new CacheCreativeSizeMetrics(
                metricRegistry, counterType, createPrefix(), CacheCreativeType.CREATIVE);
        cacheCreativeTtlMetrics = new CacheCreativeTtlMetrics(
                metricRegistry, counterType, createPrefix(), CacheCreativeType.CREATIVE);
        cacheVtrackMetrics = new CacheVtrackMetrics(metricRegistry, counterType, createPrefix());
        cacheModuleStorageMetrics = new HashMap<>();
        cacheModuleStorageMetricsCreator = moduleCode ->
                new CacheModuleStorageMetrics(metricRegistry, counterType, createPrefix(), moduleCode);
    }

    CacheMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        requestsMetrics = new RequestMetrics(metricRegistry, counterType, createPrefix(prefix));
        cacheCreativeSizeMetrics = new CacheCreativeSizeMetrics(
                metricRegistry, counterType, createPrefix(prefix), CacheCreativeType.CREATIVE);
        cacheCreativeTtlMetrics = new CacheCreativeTtlMetrics(
                metricRegistry, counterType, createPrefix(prefix), CacheCreativeType.CREATIVE);
        cacheVtrackMetrics = new CacheVtrackMetrics(metricRegistry, counterType, createPrefix(prefix));
        cacheModuleStorageMetrics = new HashMap<>();
        cacheModuleStorageMetricsCreator = moduleCode ->
                new CacheModuleStorageMetrics(metricRegistry, counterType, createPrefix(), moduleCode);
    }

    private static String createPrefix(String prefix) {
        return "%s.%s".formatted(prefix, createPrefix());
    }

    private static String createPrefix() {
        return "prebid_cache";
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    RequestMetrics requests() {
        return requestsMetrics;
    }

    CacheCreativeSizeMetrics creativeSize() {
        return cacheCreativeSizeMetrics;
    }

    CacheCreativeTtlMetrics creativeTtl() {
        return cacheCreativeTtlMetrics;
    }

    CacheVtrackMetrics vtrack() {
        return cacheVtrackMetrics;
    }

    CacheModuleStorageMetrics moduleStorage(String moduleCode) {
        return cacheModuleStorageMetrics.computeIfAbsent(moduleCode, cacheModuleStorageMetricsCreator);
    }
}

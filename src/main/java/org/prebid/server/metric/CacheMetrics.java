package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Cache metrics support.
 */
class CacheMetrics extends UpdatableMetrics {

    private final RequestMetrics requestsMetrics;
    private final CacheCreativeSizeMetrics cacheCreativeSizeMetrics;

    CacheMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix()));

        requestsMetrics = new RequestMetrics(metricRegistry, counterType, createPrefix());
        cacheCreativeSizeMetrics = new CacheCreativeSizeMetrics(metricRegistry, counterType, createPrefix());
    }

    CacheMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        requestsMetrics = new RequestMetrics(metricRegistry, counterType, createPrefix(prefix));
        cacheCreativeSizeMetrics = new CacheCreativeSizeMetrics(metricRegistry, counterType, createPrefix(prefix));
    }

    private static String createPrefix(String prefix) {
        return String.format("%s.%s", prefix, createPrefix());
    }

    private static String createPrefix() {
        return "prebid_cache";
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    RequestMetrics requests() {
        return requestsMetrics;
    }

    CacheCreativeSizeMetrics creativeSize() {
        return cacheCreativeSizeMetrics;
    }
}

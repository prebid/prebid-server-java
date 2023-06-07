package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Cache metrics support.
 */
class CacheMetrics extends UpdatableMetrics {

    private final RequestMetrics requestsMetrics;
    private final CacheCreativeSizeMetrics cacheCreativeSizeMetrics;

    CacheMetrics(MeterRegistry meterRegistry, CounterType counterType) {
        super(
                Objects.requireNonNull(meterRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix()));

        requestsMetrics = new RequestMetrics(meterRegistry, counterType, createPrefix());
        cacheCreativeSizeMetrics = new CacheCreativeSizeMetrics(meterRegistry, counterType, createPrefix());
    }

    CacheMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(meterRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        requestsMetrics = new RequestMetrics(meterRegistry, counterType, createPrefix(prefix));
        cacheCreativeSizeMetrics = new CacheCreativeSizeMetrics(meterRegistry, counterType, createPrefix(prefix));
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
}

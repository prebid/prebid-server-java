package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Adapter metrics support.
 */
class AdapterMetrics extends UpdatableMetrics {

    private final Function<String, AdapterTypeMetrics> adapterMetricsCreator;
    private final Map<String, AdapterTypeMetrics> adapterMetrics;

    AdapterMetrics(MeterRegistry meterRegistry, CounterType counterType, String accountPrefix) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType),
                nameCreator(createAdapterSuffix(Objects.requireNonNull(accountPrefix))));

        adapterMetrics = new HashMap<>();
        adapterMetricsCreator = adapterType -> new AdapterTypeMetrics(meterRegistry, counterType,
                createAdapterSuffix(Objects.requireNonNull(accountPrefix)), adapterType);
    }

    private static String createAdapterSuffix(String prefix) {
        return prefix + ".adapter";
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    AdapterTypeMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }
}

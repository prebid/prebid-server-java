package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

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

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String accountPrefix) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createAdapterSuffix(Objects.requireNonNull(accountPrefix))));

        adapterMetrics = new HashMap<>();
        adapterMetricsCreator = adapterType -> new AdapterTypeMetrics(metricRegistry, counterType,
                createAdapterSuffix(Objects.requireNonNull(accountPrefix)), adapterType);
    }

    private static String createAdapterSuffix(String prefix) {
        return String.format("%s.adapter", prefix);
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    AdapterTypeMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }
}

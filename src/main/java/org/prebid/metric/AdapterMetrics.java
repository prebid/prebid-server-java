package org.prebid.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

public class AdapterMetrics extends UpdatableMetrics {

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String adapterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(String.format("adapter.%s", Objects.requireNonNull(adapterType))));
    }

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String account, String adapterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(String.format("account.%s.%s", Objects.requireNonNull(account),
                        Objects.requireNonNull(adapterType))));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.name());
    }
}

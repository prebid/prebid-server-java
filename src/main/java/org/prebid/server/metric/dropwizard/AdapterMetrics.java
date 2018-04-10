package org.prebid.server.metric.dropwizard;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.MetricName;

import java.util.Objects;
import java.util.function.Function;

/**
 * Registry of metrics for an account metrics support.
 */
public class AdapterMetrics extends UpdatableMetrics implements org.prebid.server.metric.AdapterMetrics {

    public AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String adapterType) {
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

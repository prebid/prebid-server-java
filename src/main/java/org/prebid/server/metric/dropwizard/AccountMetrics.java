package org.prebid.server.metric.dropwizard;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.MetricName;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Registry of {@link AdapterMetrics} for account metrics support.
 */
public class AccountMetrics extends UpdatableMetrics implements org.prebid.server.metric.AccountMetrics {

    private final Function<String, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<String, AdapterMetrics> adapterMetrics;

    public AccountMetrics(MetricRegistry metricRegistry, CounterType counterType, String account) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(account)));
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, account, adapterType);
        adapterMetrics = new HashMap<>();
    }

    private static Function<MetricName, String> nameCreator(String account) {
        return metricName -> String.format("account.%s.%s", account, metricName.name());
    }

    /**
     * Returns existing or creates a new {@link AdapterMetrics}.
     */
    @Override
    public AdapterMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }
}

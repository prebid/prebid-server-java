package org.prebid.server.metric.dropwizard;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.CounterType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Packages different categories (instances) of metrics
 */
public class Metrics extends UpdatableMetrics implements org.prebid.server.metric.Metrics {

    private final Function<String, AccountMetrics> accountMetricsCreator;
    private final Function<String, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<String, AccountMetrics> accountMetrics;
    private final Map<String, AdapterMetrics> adapterMetrics;
    private final CookieSyncMetrics cookieSyncMetrics;

    public Metrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(metricRegistry, counterType, Enum::name);

        accountMetricsCreator = account -> new AccountMetrics(metricRegistry, counterType, account);
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, adapterType);
        accountMetrics = new HashMap<>();
        adapterMetrics = new HashMap<>();
        cookieSyncMetrics = new CookieSyncMetrics(metricRegistry, counterType);
    }

    /**
     * Returns existing or creates a new {@link AccountMetrics}.
     */
    @Override
    public AccountMetrics forAccount(String account) {
        return accountMetrics.computeIfAbsent(account, accountMetricsCreator);
    }

    /**
     * Returns existing or creates a new {@link AdapterMetrics}.
     */
    @Override
    public AdapterMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }

    /**
     * Returns {@link CookieSyncMetrics}.
     */
    @Override
    public CookieSyncMetrics cookieSync() {
        return cookieSyncMetrics;
    }

}

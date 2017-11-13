package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.config.ApplicationConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class Metrics extends UpdatableMetrics {

    private final Function<String, AccountMetrics> accountMetricsCreator;
    private final Function<Adapter.Type, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<String, AccountMetrics> accountMetrics;
    private final Map<Adapter.Type, AdapterMetrics> adapterMetrics;

    private Metrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(metricRegistry, counterType, Enum::name);
        accountMetricsCreator = account -> new AccountMetrics(metricRegistry, counterType, account);
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, adapterType);
        accountMetrics = new HashMap<>();
        adapterMetrics = new HashMap<>();
    }

    public static Metrics create(MetricRegistry metricRegistry, ApplicationConfig config) {
        Objects.requireNonNull(metricRegistry);
        Objects.requireNonNull(config);

        return new Metrics(metricRegistry, CounterType.valueOf(config.getString("metrics.metricType")));
    }

    public AccountMetrics forAccount(String account) {
        Objects.requireNonNull(account);
        return accountMetrics.computeIfAbsent(account, accountMetricsCreator);
    }

    public AdapterMetrics forAdapter(Adapter.Type adapterType) {
        Objects.requireNonNull(adapterType);
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }
}

package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import org.rtb.vexing.adapter.Adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class AccountMetrics extends UpdatableMetrics {

    private final Function<Adapter.Type, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<Adapter.Type, AdapterMetrics> adapterMetrics;

    AccountMetrics(MetricRegistry metricRegistry, CounterType counterType, String account) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType), nameCreator(account));
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, account, adapterType);
        adapterMetrics = new HashMap<>();
    }

    private static Function<MetricName, String> nameCreator(String account) {
        Objects.requireNonNull(account);
        return metricName -> String.format("account.%s.%s", account, metricName.name());
    }

    public AdapterMetrics forAdapter(Adapter.Type adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }
}

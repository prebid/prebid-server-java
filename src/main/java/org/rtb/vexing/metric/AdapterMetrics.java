package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import org.rtb.vexing.adapter.Adapter;

import java.util.Objects;

public class AdapterMetrics extends UpdatableMetrics {

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, Adapter.Type adapterType) {
        this(metricRegistry, counterType, String.format("adapter.%s", Objects.requireNonNull(adapterType).name()));
    }

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String account, Adapter.Type adapterType) {
        this(metricRegistry, counterType, String.format("account.%s.%s", Objects.requireNonNull(account),
                Objects.requireNonNull(adapterType).name()));
    }

    private AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                metricName -> String.format("%s.%s", prefix, metricName.name()));
    }
}

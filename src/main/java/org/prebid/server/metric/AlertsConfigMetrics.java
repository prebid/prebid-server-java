package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

public class AlertsConfigMetrics extends UpdatableMetrics {

    AlertsConfigMetrics(MeterRegistry meterRegistry, CounterType counterType, String account) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType),
                nameCreator(account));
    }

    private static Function<MetricName, String> nameCreator(String account) {
        return metricName -> "alerts.account_config.%s.%s".formatted(account, metricName);
    }
}

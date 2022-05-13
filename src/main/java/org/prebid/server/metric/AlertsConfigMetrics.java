package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

public class AlertsConfigMetrics extends UpdatableMetrics {

    AlertsConfigMetrics(MetricRegistry metricRegistry, CounterType counterType, String account) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(account));
    }

    private static Function<MetricName, String> nameCreator(String account) {
        return metricName -> String.format("alerts.account_config.%s.%s", account, metricName.toString());
    }
}

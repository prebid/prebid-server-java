package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.function.Function;

public class AlertsAccountConfigMetric extends UpdatableMetrics {

    AlertsAccountConfigMetric(MetricRegistry metricRegistry, CounterType counterType, String prefix, String account) {
        super(metricRegistry, counterType, nameCreator(prefix, account));
    }

    private static Function<MetricName, String> nameCreator(String prefix, String account) {
        return metricName -> "%s.account_config.%s.%s".formatted(prefix, account, metricName);
    }
}


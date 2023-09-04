package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.Function;

public class AlertsAccountConfigMetric extends UpdatableMetrics {

    AlertsAccountConfigMetric(MeterRegistry meterRegistry, CounterType counterType, String prefix, String account) {
        super(meterRegistry, counterType, nameCreator(prefix, account));
    }

    private static Function<MetricName, String> nameCreator(String prefix, String account) {
        return metricName -> "%s.account_config.%s.%s".formatted(prefix, account, metricName);
    }
}


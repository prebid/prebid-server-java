package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class AlertsConfigMetrics extends UpdatableMetrics {

    private final Function<String, AlertsAccountConfigMetric> alertsAccountConfigMetricsCreator;
    private final Map<String, AlertsAccountConfigMetric> alertsAccountConfigMetrics;

    AlertsConfigMetrics(MeterRegistry meterRegistry, CounterType counterType) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType), nameCreator());

        alertsAccountConfigMetricsCreator = account -> new AlertsAccountConfigMetric(
                meterRegistry, counterType, prefix(), account);
        alertsAccountConfigMetrics = new HashMap<>();
    }

    private static Function<MetricName, String> nameCreator() {
        return metricName -> "%s.%s".formatted(prefix(), metricName);
    }

    private static String prefix() {
        return "alerts";
    }

    public AlertsAccountConfigMetric accountConfig(String account) {
        return alertsAccountConfigMetrics.computeIfAbsent(account, alertsAccountConfigMetricsCreator);
    }
}

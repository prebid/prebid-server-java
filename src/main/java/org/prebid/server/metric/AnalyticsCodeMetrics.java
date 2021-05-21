package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Analytics by code metrics support.
 */
class AnalyticsCodeMetrics extends UpdatableMetrics {

    private final Function<MetricName, EventTypeMetrics> eventTypeMetricsCreator;
    private final Map<MetricName, EventTypeMetrics> eventTypeMetrics;

    AnalyticsCodeMetrics(MetricRegistry metricRegistry, CounterType counterType, String analyticCode) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createAdapterPrefix(Objects.requireNonNull(analyticCode))));

        eventTypeMetricsCreator = eventType ->
                new EventTypeMetrics(metricRegistry, counterType, createAdapterPrefix(analyticCode), eventType);
        eventTypeMetrics = new HashMap<>();
    }

    private static String createAdapterPrefix(String adapterType) {
        return String.format("analytics.%s", adapterType);
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    EventTypeMetrics forEventType(MetricName eventType) {
        return eventTypeMetrics.computeIfAbsent(eventType, eventTypeMetricsCreator);
    }
}

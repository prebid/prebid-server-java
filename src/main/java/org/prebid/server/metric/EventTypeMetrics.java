package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.function.Function;

/**
 * Metrics for reporting on certain event type
 */
public class EventTypeMetrics extends UpdatableMetrics {

    EventTypeMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, MetricName eventType) {
        super(metricRegistry, counterType, nameCreator(prefix, eventType));
    }

    private static Function<MetricName, String> nameCreator(String prefix, MetricName eventType) {
        return metricName -> String.format("%s.%s.%s", prefix, eventType, metricName.toString());
    }
}

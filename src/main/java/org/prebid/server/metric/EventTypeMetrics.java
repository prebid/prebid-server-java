package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.Function;

/**
 * Metrics for reporting on certain event type
 */
public class EventTypeMetrics extends UpdatableMetrics {

    EventTypeMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix, MetricName eventType) {
        super(meterRegistry, counterType, nameCreator(prefix, eventType));
    }

    private static Function<MetricName, String> nameCreator(String prefix, MetricName eventType) {
        return metricName -> "%s.%s.%s".formatted(prefix, eventType, metricName);
    }
}

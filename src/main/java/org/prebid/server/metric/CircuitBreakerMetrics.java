package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Circuit breaker metrics support.
 */
class CircuitBreakerMetrics extends UpdatableMetrics {

    CircuitBreakerMetrics(MetricRegistry metricRegistry, CounterType counterType, String id) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(id)));
    }

    private static Function<MetricName, String> nameCreator(String id) {
        return metricName -> String.format("%s.%s", metricName.toString(), id);
    }
}

package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class RequestMetrics extends UpdatableMetrics {

    RequestMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix)));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.requests.%s".formatted(prefix, metricName);
    }
}

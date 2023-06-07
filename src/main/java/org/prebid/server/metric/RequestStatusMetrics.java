package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class RequestStatusMetrics extends UpdatableMetrics {

    RequestStatusMetrics(MeterRegistry meterRegistry, CounterType counterType, MetricName requestType) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(requestType)));
    }

    private static Function<MetricName, String> nameCreator(MetricName requestType) {
        return metricName -> "requests.%s.%s".formatted(metricName, requestType);
    }
}

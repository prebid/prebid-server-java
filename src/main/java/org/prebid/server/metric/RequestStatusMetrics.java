package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class RequestStatusMetrics extends UpdatableMetrics {

    RequestStatusMetrics(MeterRegistry meterRegistry, MetricName requestType) {
        super(Objects.requireNonNull(meterRegistry),
                nameCreator(Objects.requireNonNull(requestType)));
    }

    private static Function<MetricName, String> nameCreator(MetricName requestType) {
        return metricName -> "requests.%s.%s".formatted(metricName, requestType);
    }
}

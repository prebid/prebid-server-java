package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
public class RequestMetrics extends UpdatableMetrics {

    RequestMetrics(MetricRegistry metricRegistry, CounterType counterType, MetricName requestType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(requestType)));
    }

    private static Function<MetricName, String> nameCreator(MetricName requestType) {
        return metricName -> String.format("requests.%s.%s", metricName.name(), requestType.name());
    }
}

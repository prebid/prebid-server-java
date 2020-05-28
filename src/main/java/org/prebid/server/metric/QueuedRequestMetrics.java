package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

public class QueuedRequestMetrics extends UpdatableMetrics {

    QueuedRequestMetrics(MetricRegistry metricRegistry, CounterType counterType, MetricName requestType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(requestType)));
    }

    private static Function<MetricName, String> nameCreator(MetricName requestType) {
        return metricName -> String.format("queued_requests.%s.%s", requestType.toString(), metricName.toString());
    }
}

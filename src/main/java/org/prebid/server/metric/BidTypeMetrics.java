package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.function.Function;

/**
 * Metrics for reporting on certain bid type
 */
class BidTypeMetrics extends UpdatableMetrics {

    BidTypeMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, String bidType) {
        super(metricRegistry, counterType, nameCreator(prefix, bidType));
    }

    private static Function<MetricName, String> nameCreator(String prefix, String bidType) {
        return metricName -> String.format("%s.%s.%s", prefix, bidType, metricName.toString());
    }
}

package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.Function;

/**
 * Metrics for reporting on certain bid type
 */
class BidTypeMetrics extends UpdatableMetrics {

    BidTypeMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix, String bidType) {
        super(meterRegistry, counterType, nameCreator(prefix, bidType));
    }

    private static Function<MetricName, String> nameCreator(String prefix, String bidType) {
        return metricName -> "%s.%s.%s".formatted(prefix, bidType, metricName);
    }
}

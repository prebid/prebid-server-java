package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

public class BidderCardinalityMetrics extends UpdatableMetrics {

    BidderCardinalityMetrics(MeterRegistry meterRegistry, Integer cardinality) {
        super(meterRegistry, nameCreator(Objects.requireNonNull(cardinality)));
    }

    private static Function<MetricName, String> nameCreator(Integer cardinality) {
        return metricName -> "bidder-cardinality.%d.%s".formatted(cardinality, metricName);
    }
}

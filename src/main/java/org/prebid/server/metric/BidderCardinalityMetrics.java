package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

public class BidderCardinalityMetrics extends UpdatableMetrics {

    BidderCardinalityMetrics(MetricRegistry metricRegistry, CounterType counterType, Integer cardinality) {
        super(metricRegistry, counterType, nameCreator(Objects.requireNonNull(cardinality)));
    }

    private static Function<MetricName, String> nameCreator(Integer cardinality) {
        return metricName -> String.format("bidder-cardinality.%d.%s", cardinality, metricName.toString());
    }
}

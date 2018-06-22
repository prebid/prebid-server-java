package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.function.Function;

/**
 * Markup delivery metrics for reporting on certain bid type
 */
public class BidTypeMetrics extends UpdatableMetrics {

    BidTypeMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, BidType bidType) {
        super(metricRegistry, counterType, nameCreator(prefix, bidType));
    }

    private static Function<MetricName, String> nameCreator(String prefix, BidType bidType) {
        return metricName -> String.format("%s.%s.%s", prefix, bidType.toString(), metricName.toString());
    }
}

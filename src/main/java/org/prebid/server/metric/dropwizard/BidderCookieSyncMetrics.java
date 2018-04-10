package org.prebid.server.metric.dropwizard;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.MetricName;

import java.util.Objects;
import java.util.function.Function;

public class BidderCookieSyncMetrics extends UpdatableMetrics
        implements org.prebid.server.metric.BidderCookieSyncMetrics {

    public BidderCookieSyncMetrics(MetricRegistry metricRegistry, CounterType counterType, String bidder) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(bidder)));
    }

    private static Function<MetricName, String> nameCreator(String bidder) {
        return metricName -> String.format("usersync.%s.%s", bidder, metricName.name());
    }
}

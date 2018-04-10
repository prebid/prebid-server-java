package org.prebid.server.metric.dropwizard;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.CounterType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Contains cookie sync metrics for a bidders metrics support.
 */
public class CookieSyncMetrics extends UpdatableMetrics implements org.prebid.server.metric.CookieSyncMetrics {

    private final Function<String, BidderCookieSyncMetrics> bidderCookieSyncMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<String, org.prebid.server.metric.BidderCookieSyncMetrics> bidderCookieSyncMetrics;

    CookieSyncMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                metricName -> String.format("usersync.%s", metricName.name()));
        bidderCookieSyncMetricsCreator = bidder -> new BidderCookieSyncMetrics(metricRegistry, counterType, bidder);
        bidderCookieSyncMetrics = new HashMap<>();
    }

    /**
     * Returns existing or create a new {@link BidderCookieSyncMetrics} for supplied bidder.
     */
    @Override
    public org.prebid.server.metric.BidderCookieSyncMetrics forBidder(String bidder) {
        return bidderCookieSyncMetrics.computeIfAbsent(bidder, bidderCookieSyncMetricsCreator);
    }
}

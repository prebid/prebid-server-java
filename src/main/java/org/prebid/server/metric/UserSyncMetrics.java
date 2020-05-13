package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Contains user sync metrics for a bidders metrics support.
 */
class UserSyncMetrics extends UpdatableMetrics {

    private final Function<String, BidderUserSyncMetrics> bidderUserSyncMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<String, BidderUserSyncMetrics> bidderUserSyncMetrics;

    UserSyncMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                metricName -> String.format("usersync.%s", metricName.toString()));
        bidderUserSyncMetricsCreator = bidder -> new BidderUserSyncMetrics(metricRegistry, counterType, bidder);
        bidderUserSyncMetrics = new HashMap<>();
    }

    BidderUserSyncMetrics forBidder(String bidder) {
        return bidderUserSyncMetrics.computeIfAbsent(bidder, bidderUserSyncMetricsCreator);
    }

    static class BidderUserSyncMetrics extends UpdatableMetrics {

        private final TcfMetrics tcfMetrics;

        BidderUserSyncMetrics(MetricRegistry metricRegistry, CounterType counterType, String bidder) {
            super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                    nameCreator(Objects.requireNonNull(createUserSyncPrefix(bidder))));
            tcfMetrics = new TcfMetrics(metricRegistry, counterType, createUserSyncPrefix(bidder));
        }

        TcfMetrics tcf() {
            return tcfMetrics;
        }

        private static String createUserSyncPrefix(String bidder) {
            return String.format("usersync.%s", bidder);
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> String.format("%s.%s", prefix, metricName.toString());
        }
    }
}

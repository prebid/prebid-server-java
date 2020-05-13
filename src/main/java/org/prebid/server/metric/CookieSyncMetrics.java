package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Contains cookie sync metrics for a bidders metrics support.
 */
public class CookieSyncMetrics extends UpdatableMetrics {

    private final Function<String, CookieSyncMetrics.BidderCookieSyncMetrics> bidderCookieSyncMetricsCreator;
    private final Map<String, CookieSyncMetrics.BidderCookieSyncMetrics> bidderCookieSyncMetrics;

    CookieSyncMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                metricName -> String.format("cookie_sync.%s", metricName.toString()));
        bidderCookieSyncMetricsCreator = bidder -> new BidderCookieSyncMetrics(metricRegistry, counterType, bidder);
        bidderCookieSyncMetrics = new HashMap<>();
    }

    CookieSyncMetrics.BidderCookieSyncMetrics forBidder(String bidder) {
        return bidderCookieSyncMetrics.computeIfAbsent(bidder, bidderCookieSyncMetricsCreator);
    }

    static class BidderCookieSyncMetrics extends UpdatableMetrics {

        private final TcfMetrics tcfMetrics;

        BidderCookieSyncMetrics(MetricRegistry metricRegistry, CounterType counterType, String bidder) {
            super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                    nameCreator(Objects.requireNonNull(createCookieSyncPrefix(bidder))));
            tcfMetrics = new TcfMetrics(metricRegistry, counterType, createCookieSyncPrefix(bidder));
        }

        TcfMetrics tcf() {
            return tcfMetrics;
        }

        private static String createCookieSyncPrefix(String bidder) {
            return String.format("cookie_sync.%s", bidder);
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> String.format("%s.%s", prefix, metricName.toString());
        }
    }
}


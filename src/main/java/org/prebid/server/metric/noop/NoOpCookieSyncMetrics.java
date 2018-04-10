package org.prebid.server.metric.noop;

import org.prebid.server.metric.BidderCookieSyncMetrics;
import org.prebid.server.metric.CookieSyncMetrics;

public class NoOpCookieSyncMetrics extends NoOpUpdatableMetrics implements CookieSyncMetrics {

    private final BidderCookieSyncMetrics bidderCookieSyncMetrics;

    public NoOpCookieSyncMetrics() {
        this.bidderCookieSyncMetrics = new NoOpBidderCookieSyncMetrics();
    }

    @Override
    public BidderCookieSyncMetrics forBidder(String bidder) {
        return this.bidderCookieSyncMetrics;
    }
}

package org.prebid.server.metric;

public interface CookieSyncMetrics extends UpdatableMetrics {

    BidderCookieSyncMetrics forBidder(String bidder);

}

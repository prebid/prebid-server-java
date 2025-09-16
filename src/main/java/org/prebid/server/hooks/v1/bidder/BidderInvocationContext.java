package org.prebid.server.hooks.v1.bidder;

import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

public interface BidderInvocationContext extends AuctionInvocationContext {

    String bidder();
}

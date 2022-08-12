package org.prebid.server.hooks.v1.bidder;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

public interface AllBidderResponsesHook extends Hook<AllBidderResponsesPayload, AuctionInvocationContext> {
}

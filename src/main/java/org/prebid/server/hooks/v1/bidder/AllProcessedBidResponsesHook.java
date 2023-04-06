package org.prebid.server.hooks.v1.bidder;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

public interface AllProcessedBidResponsesHook
        extends Hook<AllProcessedBidResponsesPayload, AuctionInvocationContext> {
}

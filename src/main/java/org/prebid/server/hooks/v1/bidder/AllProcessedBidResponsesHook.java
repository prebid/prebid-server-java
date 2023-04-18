package org.prebid.server.hooks.v1.bidder;

import org.prebid.server.hooks.v1.Hook;

public interface AllProcessedBidResponsesHook
        extends Hook<AllProcessedBidResponsesPayload, BidResponsesInvocationContext> {
}

package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public interface FetchFilter {

    FilterResult shouldInvoke(AuctionRequestPayload payload, AuctionInvocationContext invocationContext);
}

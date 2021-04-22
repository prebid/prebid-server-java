package org.prebid.server.hooks.execution.v1.bidder;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidderInvocationContextImpl implements BidderInvocationContext {

    @Delegate
    AuctionInvocationContext auctionInvocationContext;

    String bidder;
}

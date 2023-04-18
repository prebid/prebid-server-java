package org.prebid.server.hooks.execution.v1.bidder;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidResponsesInvocationContext;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidResponsesInvocationContextImpl implements BidResponsesInvocationContext {

    @Delegate
    AuctionInvocationContext auctionInvocationContext;

    BidRequest bidRequest;
}

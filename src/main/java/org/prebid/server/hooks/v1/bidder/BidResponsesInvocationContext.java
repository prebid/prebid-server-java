package org.prebid.server.hooks.v1.bidder;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

public interface BidResponsesInvocationContext extends AuctionInvocationContext {

    BidRequest bidRequest();
}

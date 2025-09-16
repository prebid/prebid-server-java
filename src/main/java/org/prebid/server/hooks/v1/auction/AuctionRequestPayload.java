package org.prebid.server.hooks.v1.auction;

import com.iab.openrtb.request.BidRequest;

public interface AuctionRequestPayload {

    BidRequest bidRequest();
}

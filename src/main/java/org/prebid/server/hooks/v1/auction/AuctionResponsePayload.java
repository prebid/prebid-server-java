package org.prebid.server.hooks.v1.auction;

import com.iab.openrtb.response.BidResponse;

public interface AuctionResponsePayload {

    BidResponse bidResponse();
}

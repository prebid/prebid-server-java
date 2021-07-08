package org.prebid.server.hooks.v1.bidder;

import org.prebid.server.bidder.model.BidderBid;

import java.util.List;

public interface BidderResponsePayload {

    List<BidderBid> bids();
}

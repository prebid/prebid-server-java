package org.prebid.server.hooks.v1.bidder;

import org.prebid.server.auction.model.BidderResponse;

import java.util.List;

public interface AllProcessedBidResponsesPayload {

    List<BidderResponse> bidResponses();
}

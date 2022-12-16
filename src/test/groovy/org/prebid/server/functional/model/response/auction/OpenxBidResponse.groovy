package org.prebid.server.functional.model.response.auction


import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest

import static org.prebid.server.functional.model.bidder.BidderName.OPENX

@ToString(includeNames = true, ignoreNulls = true)
class OpenxBidResponse extends BidResponse {

    OpenxBidResponseExt ext

    static OpenxBidResponse getDefaultBidResponse(BidRequest bidRequest) {
        def openxBidResponse = new OpenxBidResponse(id: bidRequest.id)
        def bids = Bid.getDefaultBids(bidRequest.imp)
        def seatBid = new SeatBid(bid: bids, seat: OPENX)
        openxBidResponse.seatbid = [seatBid]
        openxBidResponse
    }
}

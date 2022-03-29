package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.auction.BidRequest

import static org.prebid.server.functional.model.bidder.BidderName.*

@ToString(includeNames = true, ignoreNulls = true)
class SeatBid {

    List<Bid> bid
    BidderName seat
    Integer group

    static SeatBid getStoredResponse(BidRequest bidRequest) {
        def bids = Bid.getDefaultBids(bidRequest.imp)
        new SeatBid(bid: bids, seat: GENERIC)
    }

}

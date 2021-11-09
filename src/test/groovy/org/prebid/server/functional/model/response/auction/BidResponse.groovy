package org.prebid.server.functional.model.response.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.model.request.auction.BidRequest

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidResponse implements ResponseModel {

    String id
    List<SeatBid> seatbid
    String bidid
    String cur
    String customdata
    Integer nbr
    BidResponseExt ext

    static BidResponse getDefaultBidResponse(BidRequest bidRequest) {
        getDefaultBidResponse(bidRequest.id, bidRequest.imp*.id)
    }

    static BidResponse getDefaultBidResponse(String id, List<String> impIds) {
        def bidResponse = new BidResponse(id: id)
        def bids = getDefaultBids(impIds)
        def seatBid = new SeatBid(bid: bids)
        bidResponse.seatbid = [seatBid]
        bidResponse
    }

    static private List<Bid> getDefaultBids(List<String> impIds) {
        impIds.collect { Bid.getDefaultBid(it) }
    }
}

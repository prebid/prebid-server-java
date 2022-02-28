package org.prebid.server.functional.model.response.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
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

    static BidResponse getDefaultPgBidResponse(BidRequest bidRequest, PlansResponse plansResponse) {
        def bidResponse = getDefaultBidResponse(bidRequest)
        def bid = bidResponse.seatbid[0].bid[0]
        def lineItem = plansResponse.lineItems[0]
        bid.dealid = lineItem.dealId
        bid.w = lineItem.sizes[0].w
        bid.h = lineItem.sizes[0].h
        bidResponse
    }

    static private List<Bid> getDefaultBids(List<String> impIds) {
        impIds.collect { Bid.getDefaultBid(it) }
    }
}

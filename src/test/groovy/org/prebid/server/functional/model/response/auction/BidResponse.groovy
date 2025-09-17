package org.prebid.server.functional.model.response.auction

<<<<<<< HEAD
import com.fasterxml.jackson.annotation.JsonProperty
=======
>>>>>>> 04d9d4a13 (Initial commit)
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.auction.BidRequest

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidResponse implements ResponseModel {

    String id
    List<SeatBid> seatbid
    String bidid
    Currency cur
    String customdata
<<<<<<< HEAD
    @JsonProperty("nbr")
    NoBidResponse noBidResponse
=======
    NoBidResponse nbr
>>>>>>> 04d9d4a13 (Initial commit)
    BidResponseExt ext

    static BidResponse getDefaultBidResponse(BidRequest bidRequest, BidderName bidderName = GENERIC) {
        def bidResponse = new BidResponse(id: bidRequest.id)
        def bids = Bid.getDefaultBids(bidRequest.imp)
        def seatBid = new SeatBid(bid: bids, seat: bidderName)
        bidResponse.seatbid = [seatBid]
        bidResponse
    }
}

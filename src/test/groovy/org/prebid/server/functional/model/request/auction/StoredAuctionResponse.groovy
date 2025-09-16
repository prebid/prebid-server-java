package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.response.auction.SeatBid

@ToString(includeNames = true, ignoreNulls = true)
class StoredAuctionResponse {

    String id
    @JsonProperty("seatbidarr")
    List<SeatBid> seatBids
    @JsonProperty("seatbidobj")
    SeatBid seatBidObject
}

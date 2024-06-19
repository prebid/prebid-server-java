package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.response.auction.SeatBid

@ToString(includeNames = true, ignoreNulls = true)
class StoredAuctionResponse {

    String id
    List<SeatBid> seatbid
}

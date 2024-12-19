package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.response.BidderError
import org.prebid.server.functional.model.response.Debug

@ToString(includeNames = true, ignoreNulls = true)
class BidResponseExt {

    Debug debug
    List<SeatNonBid> seatnonbid
    Map<ErrorType, List<BidderError>> errors
    Map<String, Integer> responsetimemillis
    Long tmaxrequest
    Map<String, ResponseSyncData> usersync
    BidResponsePrebid prebid
    Map<ErrorType, List<WarningEntry>> warnings
    @JsonProperty("igi")
    List<InterestGroupAuctionIntent> interestGroupAuctionIntent
}

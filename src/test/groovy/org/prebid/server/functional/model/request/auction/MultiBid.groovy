package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = false)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class MultiBid {

    BidderName bidder
    List<String> bidders
    Integer maxBids
    String targetBidderCodePrefix
}

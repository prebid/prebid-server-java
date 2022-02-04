package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = false)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class MultiBid {

    String bidder
    List<String> bidders
    Integer maxBids
    String targetBidderCodePrefix
}

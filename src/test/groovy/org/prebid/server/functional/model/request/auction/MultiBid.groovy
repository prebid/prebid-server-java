package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = false)
@JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
class MultiBid {

    String bidder
    List<String> bidders
    Integer maxBids
    String targetBidderCodePrefix
}

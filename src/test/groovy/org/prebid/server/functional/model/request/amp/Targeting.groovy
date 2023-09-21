package org.prebid.server.functional.model.request.amp

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.User

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class Targeting {

    List<BidderName> bidders
    Site site
    User user
    List<String> keywords
}

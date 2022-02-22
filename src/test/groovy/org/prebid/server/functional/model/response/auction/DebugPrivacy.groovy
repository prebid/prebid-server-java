package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class DebugPrivacy {

    Privacy originPrivacy
    Privacy resolvedPrivacy
    Map<BidderName, Set<String>> privacyActionsPerBidder
    List<String> errors
}

package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class BidAdjustmentRule {

    @JsonProperty('*')
    Map<String, List<AdjustmentRule>> wildcardBidder
    Map<String, List<AdjustmentRule>> generic
    Map<String, List<AdjustmentRule>> openx
    Map<String, List<AdjustmentRule>> alias
}

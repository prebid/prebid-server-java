package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class BidAdjustmentRule {

    @JsonProperty('*')
    Map<String, List<AdjustmentRule>> wildcardBidder
    Map<String, List<AdjustmentRule>> generic
    Map<String, List<AdjustmentRule>> alias
    Map<String, List<AdjustmentRule>> amx
    @JsonProperty("ALIAS")
    Map<String, List<AdjustmentRule>> aliasUpperCase
    @JsonProperty("AlIaS")
    Map<String, List<AdjustmentRule>> aliasCamelCase
}

package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class ImpExtContextData {

    String language
    List<String> keywords
    Integer buyerId
    List<Integer> buyerIds
    String pbAdSlot
    ImpExtContextDataAdServer adServer
}

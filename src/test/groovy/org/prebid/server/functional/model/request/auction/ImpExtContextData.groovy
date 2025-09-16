package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ImpExtContextData {

    String language
    List<String> keywords
    Integer buyerId
    List<Integer> buyerIds
    String pbAdSlot
    String adSlot
    ImpExtContextDataAdServer adServer
    String any
}

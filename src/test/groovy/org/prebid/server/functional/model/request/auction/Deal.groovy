package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class Deal {

    String id
    Float bidFloor
    String bidFloorCur
    Integer at
    List<String> wseat
    List<String> wadomain
    DealExt ext
}

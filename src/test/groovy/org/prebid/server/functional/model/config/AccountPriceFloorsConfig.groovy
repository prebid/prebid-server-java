package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountPriceFloorsConfig {

    Boolean enabled
    PriceFloorsFetch fetch
    Integer enforceFloorsRate
    Boolean adjustForBidAdjustment
    Boolean enforceDealFloors
    Boolean useDynamicData
}

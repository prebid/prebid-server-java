package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class PriceFloorEnforcement {

    Boolean enforceJS
    @JsonProperty("enforcePBS")
    Boolean enforcePbs
    Boolean floorDeals
    Boolean bidAdjustment
}

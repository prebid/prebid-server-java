package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
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

    @JsonProperty("enforce_floors_rate")
    Integer enforceFloorsRateSnakeCase
    @JsonProperty("adjust_for_bid_adjustment")
    Boolean adjustForBidAdjustmentSnakeCase
    @JsonProperty("enforce_deal_floors")
    Boolean enforceDealFloorsSnakeCase
    @JsonProperty("use_dynamic_data")
    Boolean useDynamicDataSnakeCase
}

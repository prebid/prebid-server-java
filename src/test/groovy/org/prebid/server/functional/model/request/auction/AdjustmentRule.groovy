package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class AdjustmentRule {

    @JsonProperty('adjtype')
    AdjustmentType adjustmentType
    BigDecimal value
    Currency currency
}

package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class BidAdjustmentFactors {

    Map<String, BigDecimal> adjustments
    EnumMap<BidAdjustmentMediaType, Map<BidderName, BigDecimal>> mediaTypes

    @JsonAnyGetter
    Map<String, BigDecimal> getAdjustments() {
        adjustments
    }

    @JsonAnySetter
    void addAdjustments(String key, BigDecimal value) {
        if (!adjustments) {
            adjustments = [:]
        }
        adjustments.put(key, value)
    }
}

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

    @JsonAnySetter
    Map<BidderName, BigDecimal> adjustments
    Map<BidAdjustmentMediaType, Map<BidderName, BigDecimal>> mediaTypes


    @JsonAnyGetter
    Map<BidderName, BigDecimal> getAdjustments() {
        adjustments
    }
}

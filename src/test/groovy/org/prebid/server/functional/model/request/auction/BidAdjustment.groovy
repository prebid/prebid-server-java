package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class BidAdjustment {

    Map<BidAdjustmentMediaType, BidAdjustmentRule> mediaType
    Integer version

    static getDefaultWithSingleMediaTypeRule(BidAdjustmentMediaType type,
                                     BidAdjustmentRule rule,
                                     Integer version = PBSUtils.randomNumber) {
        new BidAdjustment(mediaType: [(type): rule], version: version)
    }
}

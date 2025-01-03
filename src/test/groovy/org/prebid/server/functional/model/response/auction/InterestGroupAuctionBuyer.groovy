package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class InterestGroupAuctionBuyer {

    String origin
    BigDecimal maxBid
    Currency cur
    Map pbs
    InterestGroupAuctionBuyerExt ext
}

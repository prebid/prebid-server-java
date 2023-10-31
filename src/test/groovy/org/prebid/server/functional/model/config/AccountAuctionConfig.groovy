package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Targeting

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountAuctionConfig {

    String priceGranularity
    Integer bannerCacheTtl
    Integer videoCacheTtl
    Integer truncateTargetAttr
    String defaultIntegration
    AccountBidValidationConfig bidValidations
    AccountEventsConfig events
    Boolean debugAllow
    AccountPriceFloorsConfig priceFloors
    Targeting targeting
}

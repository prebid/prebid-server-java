package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
class AccountAuctionConfig {

    String priceGranularity
    Integer bannerCacheTtl
    Integer videoCacheTtl
    Integer truncateTargetAttr
    String defaultIntegration
    AccountBidValidationConfig bidValidations
    AccountEventsConfig events
    Boolean debugAllow
}

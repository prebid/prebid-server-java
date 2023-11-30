package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.MediaType

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
    @JsonProperty("preferredmediatype")
    Map<BidderName, MediaType> preferredMediaType
}

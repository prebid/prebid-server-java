package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.auction.BidAdjustment
import org.prebid.server.functional.model.request.auction.BidRounding
import org.prebid.server.functional.model.request.auction.PaaFormat
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.MediaType

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountAuctionConfig {

    PriceGranularityType priceGranularity
    Integer bannerCacheTtl
    Integer videoCacheTtl
    Integer truncateTargetAttr
    String defaultIntegration
    Boolean debugAllow
    AccountBidValidationConfig bidValidations
    AccountEventsConfig events
    AccountCacheConfig cache
    AccountRankingConfig ranking
    AccountPriceFloorsConfig priceFloors
    AccountProfilesConfigs profiles
    Targeting targeting
    PaaFormat paaformat
    @JsonProperty("preferredmediatype")
    Map<BidderName, MediaType> preferredMediaType
    @JsonProperty("privacysandbox")
    PrivacySandbox privacySandbox
    @JsonProperty("bidadjustments")
    BidAdjustment bidAdjustments
    BidRounding bidRounding
    Integer impressionLimit

    @JsonProperty("price_granularity")
    PriceGranularityType priceGranularitySnakeCase
    @JsonProperty("banner_cache_ttl")
    Integer bannerCacheTtlSnakeCase
    @JsonProperty("video_cache_ttl")
    Integer videoCacheTtlSnakeCase
    @JsonProperty("truncate_target_attr")
    Integer truncateTargetAttrSnakeCase
    @JsonProperty("default_integration")
    String defaultIntegrationSnakeCase
    @JsonProperty("debug_allow")
    Boolean debugAllowSnakeCase
    @JsonProperty("bid_validation")
    AccountBidValidationConfig bidValidationsSnakeCase
    @JsonProperty("price_floors")
    AccountPriceFloorsConfig priceFloorsSnakeCase
    @JsonProperty("bid_rounding")
    BidRounding bidRoundingSnakeCase
    @JsonProperty("impression_limit")
    Integer impressionLimitSnakeCase

}

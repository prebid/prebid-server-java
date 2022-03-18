package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class AccountAuctionConfig {

    @JsonProperty("price-granularity")
    String priceGranularity;

    @JsonProperty("banner-cache-ttl")
    Integer bannerCacheTtl;

    @JsonProperty("video-cache-ttl")
    Integer videoCacheTtl;

    @JsonProperty("truncate-target-attr")
    Integer truncateTargetAttr;

    @JsonProperty("default-integration")
    String defaultIntegration;

    @JsonProperty("debug-allow")
    Boolean debugAllow;

    @JsonProperty("bid-validations")
    AccountBidValidationConfig bidValidations;

    AccountEventsConfig events;

    @JsonProperty("price-floors")
    AccountPriceFloorsConfig priceFloors;
}

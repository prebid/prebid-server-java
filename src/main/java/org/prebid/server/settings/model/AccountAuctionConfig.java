package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class AccountAuctionConfig {

    @JsonAlias("price-granularity")
    String priceGranularity;

    @JsonAlias("banner-cache-ttl")
    Integer bannerCacheTtl;

    @JsonAlias("video-cache-ttl")
    Integer videoCacheTtl;

    @JsonAlias("truncate-target-attr")
    Integer truncateTargetAttr;

    @JsonAlias("default-integration")
    String defaultIntegration;

    @JsonAlias("debug-allow")
    Boolean debugAllow;

    @JsonAlias("bid-validations")
    AccountBidValidationConfig bidValidations;

    AccountEventsConfig events;

    @JsonAlias("price-floors")
    AccountPriceFloorsConfig priceFloors;

    AccountTargetingConfig targeting;

    @JsonProperty("preferredmediatype")
    Map<String, MediaType> preferredMediaTypes;

    @JsonProperty("privacysandbox")
    AccountPrivacySandboxConfig privacySandbox;
}

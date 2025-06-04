package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.PaaFormat;
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

    @JsonProperty("bidadjustments")
    ObjectNode bidAdjustments;

    AccountEventsConfig events;

    @JsonAlias("price-floors")
    AccountPriceFloorsConfig priceFloors;

    AccountTargetingConfig targeting;

    @JsonAlias("bid-rounding")
    AccountAuctionBidRoundingMode bidRounding;

    @JsonProperty("preferredmediatype")
    Map<String, MediaType> preferredMediaTypes;

    @JsonProperty("privacysandbox")
    AccountPrivacySandboxConfig privacySandbox;

    @JsonProperty("paaformat")
    PaaFormat paaFormat;

    AccountCacheConfig cache;
}

package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ExtImpSharethroughCreativeMetadata {

    String action;

    String advertiser;

    @JsonProperty("advertiser_key")
    String advertiserKey;

    ExtImpSharethroughCreativeBeacons beacons;

    @JsonProperty("brand_logo_url")
    String brandLogoUrl;

    @JsonProperty("campaign_key")
    String campaignKey;

    @JsonProperty("creative_key")
    String creativeKey;

    @JsonProperty("custom_engagement_action")
    String customEngagementAction;

    @JsonProperty("custom_engagement_label")
    String customEngagementLabel;

    @JsonProperty("custom_engagement_url")
    String customEngagementUrl;

    @JsonProperty("deal_id")
    String dealId;

    String description;

    @JsonProperty("force_click_to_play")
    boolean forceClickToPlay;

    @JsonProperty("icon_url")
    String iconUrl;

    @JsonProperty("impression_html")
    String impressionHtml;

    @JsonProperty("instant_play_mobile_count")
    int instantPlayMobileCount;

    @JsonProperty("instant_play_mobile_url")
    String instantPlayMobileUrl;

    @JsonProperty("media_url")
    String mediaUrl;

    @JsonProperty("share_url")
    String shareUrl;

    @JsonProperty("source_id")
    String sourceId;

    @JsonProperty("thumbnail_url")
    String thumbnailUrl;

    String title;

    @JsonProperty("variant_key")
    String variantKey;

}

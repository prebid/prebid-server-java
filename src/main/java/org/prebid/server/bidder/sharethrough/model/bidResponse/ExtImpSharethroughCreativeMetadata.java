package org.prebid.server.bidder.sharethrough.model.bidResponse;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ExtImpSharethroughCreativeMetadata {

    String action;

    String advertiser;

    String advertiserKey;

    ExtImpSharethroughCreativeBeacons beacons;

    String brandLogoUrl;

    String campaignKey;

    String creativeKey;

    String customEngagementAction;

    String customEngagementLabel;

    String customEngagementUrl;

    String dealId;

    String description;

    Boolean forceClickToPlay;

    String iconUrl;

    String impressionHtml;

    Integer instantPlayMobileCount;

    String instantPlayMobileUrl;

    String mediaUrl;

    String shareUrl;

    String sourceId;

    String thumbnailUrl;

    String title;

    String variantKey;
}


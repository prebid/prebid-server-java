package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ExtImpSharethroughPlacementAttributes {
    @JsonProperty("ad_server_key")
    String adServerKey;

    @JsonProperty("ad_server_path")
    String adServerPath;

    @JsonProperty("allow_dynamic_cropping")
    boolean allowDynamicCropping;

    @JsonProperty("app_third_party_partners")
    List<String> appThirdPartyPartners;

    @JsonProperty("custom_card_css")
    String customCardCss;

    @JsonProperty("dfp_path")
    String dfpPath;

    @JsonProperty("direct_sell_promoted_by_text")
    String directSellPromotedByText;

    String domain;

    @JsonProperty("enable_link_redirection")
    boolean enableLinkRedirection;

    @JsonProperty("featured_content")
    JsonNode featuredContent;

    @JsonProperty("max_headline_length")
    int maxHeadlineLength;

    @JsonProperty("multi_ad_placement")
    boolean multiAdPlacement;

    @JsonProperty("promoted_by_text")
    String promotedByText;

    @JsonProperty("publisher_key")
    String publisherKey;

    @JsonProperty("rendering_pixel_offset")
    int renderingPixelOffset;

    @JsonProperty("safe_frame_size")
    List<Integer> safeFrameSize;

    @JsonProperty("site_key")
    String siteKey;

    @JsonProperty("str_opt_out_url")
    String strOptOutUrl;

    String template;

    @JsonProperty("third_party_partners")
    List<ExtImpSharethroughPlacementThirdPartyPartner> thirdPartyPartners;
}

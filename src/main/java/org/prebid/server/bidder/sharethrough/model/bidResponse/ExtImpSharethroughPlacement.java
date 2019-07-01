package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ExtImpSharethroughPlacement {

    @JsonProperty("allow_instant_play")
    boolean allowInstantPlay;

    @JsonProperty("articles_before_first_ad")
    int articlesBeforeFirstAd;

    @JsonProperty("articles_between_ads")
    int articlesBetweenAds;

    String layout;

    JsonNode metadata;

    ExtImpSharethroughPlacementAttributes placementAttributes;

    String status;

}

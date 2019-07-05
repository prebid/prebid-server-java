package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
class ExtImpSharethroughPlacement {

    boolean allowInstantPlay;

    int articlesBeforeFirstAd;

    int articlesBetweenAds;

    String layout;

    JsonNode metadata;

    @JsonProperty("placementAttributes")
    ExtImpSharethroughPlacementAttributes placementAttributes;

    String status;

}

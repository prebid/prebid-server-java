package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
class ExtImpSharethroughPlacement {

    Boolean allowInstantPlay;

    Integer articlesBeforeFirstAd;

    Integer articlesBetweenAds;

    String layout;

    JsonNode metadata;

    @JsonProperty("placementAttributes")
    ExtImpSharethroughPlacementAttributes placementAttributes;

    String status;
}


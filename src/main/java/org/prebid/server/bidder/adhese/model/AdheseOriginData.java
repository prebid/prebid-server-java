package org.prebid.server.bidder.adhese.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdheseOriginData {

    String priority;

    @JsonProperty("orderProperty")
    String orderProperty;

    @JsonProperty("adFormat")
    String adFormat;

    @JsonProperty("adType")
    String adType;

    @JsonProperty("adspaceId")
    String adspaceId;

    @JsonProperty("libId")
    String libId;

    @JsonProperty("slotId")
    String slotId;

    @JsonProperty("viewableImpressionCounter")
    String viewableImpressionCounter;
}

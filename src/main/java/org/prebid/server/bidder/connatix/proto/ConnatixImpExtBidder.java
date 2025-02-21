package org.prebid.server.bidder.connatix.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ConnatixImpExtBidder {

    String type;

    @JsonProperty(value = "placementId")
    String placementId;

    @JsonProperty(value = "viewabilityPercentage")
    Float viewabilityPercentage;

}

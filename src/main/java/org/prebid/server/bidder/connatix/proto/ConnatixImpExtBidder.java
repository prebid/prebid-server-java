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

    // KATIE: viewability percentage, viewability container, and bidfloor are all optional. should they be included?
    @JsonProperty(value = "viewabilityPercentage")
    Float viewabilityPercentage;

}

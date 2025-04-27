package org.prebid.server.bidder.resetdigital.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ResetDigitalImpZone {

    @JsonProperty("placementId")
    String placementId;
}

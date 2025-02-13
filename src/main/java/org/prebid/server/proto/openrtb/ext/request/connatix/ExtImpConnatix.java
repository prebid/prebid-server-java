package org.prebid.server.proto.openrtb.ext.request.connatix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpConnatix {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("viewabilityPercentage")
    Float viewabilityPercentage;

}

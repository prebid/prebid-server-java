package org.prebid.server.proto.openrtb.ext.request.connatix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value(staticConstructor = "of")
public class ExtImpConnatix {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("viewabilityPercentage")
    Float viewabilityPercentage;

}

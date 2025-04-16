package org.prebid.server.proto.openrtb.ext.request.improvedigital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpImprovedigital {

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("publisherId")
    Integer publisherId;
}

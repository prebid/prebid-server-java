package org.prebid.server.proto.openrtb.ext.request.undertone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpUndertone {

    @JsonProperty("publisherId")
    Integer publisherId;

    @JsonProperty("placementId")
    Integer placementId;

}

package org.prebid.server.proto.openrtb.ext.request.undertone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpUndertone {

    @JsonProperty("publisherId")
    Integer publisherId;

    @JsonProperty("placementId")
    Integer placementId;

}

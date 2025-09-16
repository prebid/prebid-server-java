package org.prebid.server.proto.openrtb.ext.request.pulsepoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpPulsepoint {

    @JsonProperty("cp")
    Integer publisherId;

    @JsonProperty("ct")
    Integer tagId;
}

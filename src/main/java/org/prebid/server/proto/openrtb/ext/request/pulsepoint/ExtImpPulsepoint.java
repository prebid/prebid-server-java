package org.prebid.server.proto.openrtb.ext.request.pulsepoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpPulsepoint {

    @JsonProperty("cp")
    Integer publisherId;

    @JsonProperty("ct")
    Integer tagId;
}

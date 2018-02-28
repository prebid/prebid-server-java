package org.prebid.server.bidder.pulsepoint.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class PulsepointParams {

    @JsonProperty("cp")
    Integer publisherId;

    @JsonProperty("ct")
    Integer tagId;

    @JsonProperty("cf")
    String adSize;
}

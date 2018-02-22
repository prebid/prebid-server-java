package org.prebid.adapter.pulsepoint.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class PulsepointParams {

    @JsonProperty("cp")
    Integer publisherId;

    @JsonProperty("ct")
    Integer tagId;

    @JsonProperty("cf")
    String adSize;
}

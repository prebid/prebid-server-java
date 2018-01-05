package org.rtb.vexing.adapter.pulsepoint.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class PulsepointParams {

    @JsonProperty("cp")
    Integer publisherId;

    @JsonProperty("ct")
    Integer tagId;

    @JsonProperty("cf")
    String adSize;
}

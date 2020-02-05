package com.iab.openrtb.request.video;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Podconfig {

    @JsonProperty("durationrangesec")
    List<Integer> durationRangeSec;

    @JsonProperty("requireexactduration")
    Boolean requireExactDuration;

    List<Pod> pods;
}


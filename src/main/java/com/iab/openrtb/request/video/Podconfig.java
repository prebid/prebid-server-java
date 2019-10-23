package com.iab.openrtb.request.video;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class Podconfig {

    @JsonProperty("durationrangesec")
    List<Integer> durationRangeSec;

    //Op
    @JsonProperty("requireexactduration")
    Boolean requireExactDuration;

    List<Pod> pods;
}

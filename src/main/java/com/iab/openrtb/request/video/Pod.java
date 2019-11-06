package com.iab.openrtb.request.video;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Pod {

    @JsonProperty("podid")
    Integer podId;

    @JsonProperty("adpoddurationsec")
    Integer adpodDurationSec;

    @JsonProperty("configid")
    String configId;
}


package com.iab.openrtb.request.video;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Pod {

    @JsonProperty("podid")
    Integer podId;

    @JsonProperty("adpoddurationsec")
    Integer adpodDurationSec;

    @JsonProperty("configid")
    String configId;
}

package org.prebid.server.proto.openrtb.ext.request.adkernel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdkernel {

    @JsonProperty("zoneId")
    Integer zoneId;
}

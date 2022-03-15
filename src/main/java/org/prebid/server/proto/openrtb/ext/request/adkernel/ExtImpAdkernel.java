package org.prebid.server.proto.openrtb.ext.request.adkernel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdkernel {

    @JsonProperty("zoneId")
    Integer zoneId;
}

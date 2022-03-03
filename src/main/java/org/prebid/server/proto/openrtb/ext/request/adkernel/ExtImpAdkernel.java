package org.prebid.server.proto.openrtb.ext.request.adkernel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ExtImpAdkernel {

    @JsonProperty("zoneId")
    Integer zoneId;

}

package org.prebid.server.proto.openrtb.ext.request.limelightdigital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLimeLightDigital {

    String host;

    @JsonProperty("publisherId")
    Integer publisherId;
}

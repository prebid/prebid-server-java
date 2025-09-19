package org.prebid.server.proto.openrtb.ext.request.teqblaze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTeqblaze {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;
}

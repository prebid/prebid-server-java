package org.prebid.server.proto.openrtb.ext.request.loyal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLoyal {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;

    @JsonProperty("type")
    String type;
}

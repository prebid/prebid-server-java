package org.prebid.server.proto.openrtb.ext.request.playdigo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpPlaydigo {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;
}

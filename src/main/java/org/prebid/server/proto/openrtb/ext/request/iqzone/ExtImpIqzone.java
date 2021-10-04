package org.prebid.server.proto.openrtb.ext.request.iqzone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpIqzone {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;
}

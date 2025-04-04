package org.prebid.server.proto.openrtb.ext.request.operaads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOperaads {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;

    @JsonProperty("publisherId")
    String publisherId;
}

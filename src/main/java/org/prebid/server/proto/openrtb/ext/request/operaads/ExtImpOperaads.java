package org.prebid.server.proto.openrtb.ext.request.operaads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpOperaads {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;

    @JsonProperty("publisherId")
    String publisherId;
}

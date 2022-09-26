package org.prebid.server.proto.openrtb.ext.request.boldwin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBoldwin {

    @JsonProperty(value = "placementId")
    String placementId;

    @JsonProperty(value = "endpointId")
    String endpointId;
}

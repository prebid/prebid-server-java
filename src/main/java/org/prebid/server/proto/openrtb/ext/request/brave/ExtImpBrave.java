package org.prebid.server.proto.openrtb.ext.request.brave;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBrave {

    @JsonProperty("placementId")
    String placementId;
}

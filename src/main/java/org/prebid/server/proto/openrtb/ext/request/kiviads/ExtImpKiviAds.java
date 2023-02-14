package org.prebid.server.proto.openrtb.ext.request.kiviads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpKiviAds {

    @JsonProperty(value = "placementId")
    String placementId;

    @JsonProperty(value = "endpointId")
    String endpointId;
}

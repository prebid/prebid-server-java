package org.prebid.server.proto.openrtb.ext.request.mobkoi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMobkoi {

    @JsonProperty("placementId")
    String placementId;
}

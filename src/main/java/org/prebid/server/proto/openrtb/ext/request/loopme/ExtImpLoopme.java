package org.prebid.server.proto.openrtb.ext.request.loopme;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLoopme {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("bundleId")
    String bundleId;

    @JsonProperty("placementId")
    String placementId;

}

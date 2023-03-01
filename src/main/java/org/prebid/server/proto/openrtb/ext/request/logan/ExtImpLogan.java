package org.prebid.server.proto.openrtb.ext.request.logan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLogan {

    @JsonProperty("placementId")
    String placementId;
}

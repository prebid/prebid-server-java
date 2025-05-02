package org.prebid.server.proto.openrtb.ext.request.cpmstar;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpCpmStar {

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("subpoolId")
    Integer subPoolId;
}

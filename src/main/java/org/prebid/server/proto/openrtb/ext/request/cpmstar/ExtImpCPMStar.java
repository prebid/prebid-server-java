package org.prebid.server.proto.openrtb.ext.request.cpmstar;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpCPMStar {

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("subpoolId")
    Integer subpoolId;
}


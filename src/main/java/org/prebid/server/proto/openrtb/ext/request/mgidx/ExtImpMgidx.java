package org.prebid.server.proto.openrtb.ext.request.mgidx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMgidx {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;
}

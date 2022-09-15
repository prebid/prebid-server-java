package org.prebid.server.proto.openrtb.ext.request.trafficgate;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTrafficGate {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("host")
    String host;
}

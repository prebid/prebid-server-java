package org.prebid.server.proto.openrtb.ext.request.contxtful;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpContxtful {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("customerId")
    String customerId;
}

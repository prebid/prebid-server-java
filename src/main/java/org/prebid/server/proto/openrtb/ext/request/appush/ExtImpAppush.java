package org.prebid.server.proto.openrtb.ext.request.appush;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAppush {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;
}

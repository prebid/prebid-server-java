package org.prebid.server.proto.openrtb.ext.request.improvedigital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpImprovedigital {

    @JsonProperty("placementId")
    Integer placementId;
}

package org.prebid.server.proto.openrtb.ext.request.bidscube;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpBidscube {

    @JsonProperty("placementId")
    String placementId;
}

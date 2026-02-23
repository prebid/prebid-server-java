package org.prebid.server.proto.openrtb.ext.request.bidscube;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBidscube {

    @JsonProperty("placementId")
    String placementId;
}

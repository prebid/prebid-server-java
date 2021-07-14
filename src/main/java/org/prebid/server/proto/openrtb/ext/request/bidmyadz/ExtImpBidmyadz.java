package org.prebid.server.proto.openrtb.ext.request.bidmyadz;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpBidmyadz {

    @JsonProperty("placementId")
    String placementId;
}

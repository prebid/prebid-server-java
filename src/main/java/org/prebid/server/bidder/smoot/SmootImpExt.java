package org.prebid.server.bidder.smoot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class SmootImpExt {

    String type;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;

    public static SmootImpExt publisher(String placementId) {
        return SmootImpExt.of("publisher", placementId, null);
    }

    public static SmootImpExt network(String endpointId) {
        return SmootImpExt.of("network", null, endpointId);
    }
}

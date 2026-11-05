package org.prebid.server.proto.openrtb.ext.request.peak226;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpPeak226 {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("region")
    String region;
}

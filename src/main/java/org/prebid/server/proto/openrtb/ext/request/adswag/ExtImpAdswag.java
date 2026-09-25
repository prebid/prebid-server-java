package org.prebid.server.proto.openrtb.ext.request.adswag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdswag {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("placementId")
    String placementId;
}

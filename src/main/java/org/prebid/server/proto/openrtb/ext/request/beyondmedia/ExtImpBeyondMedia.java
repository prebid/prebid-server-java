package org.prebid.server.proto.openrtb.ext.request.beyondmedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBeyondMedia {

    @JsonProperty(value = "placementId")
    String placementId;
}

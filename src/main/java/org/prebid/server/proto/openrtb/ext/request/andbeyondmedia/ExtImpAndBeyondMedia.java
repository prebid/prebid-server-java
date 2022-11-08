package org.prebid.server.proto.openrtb.ext.request.andbeyondmedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAndBeyondMedia {

    @JsonProperty(value = "placementId")
    String placementId;
}

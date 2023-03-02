package org.prebid.server.proto.openrtb.ext.request.videoheroes;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpVideoHeroes {

    @JsonProperty("placementId")
    String placementId;
}

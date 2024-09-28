package org.prebid.server.proto.openrtb.ext.request.mediago;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class MediaGoImpExt {

    String token;

    String region;

    @JsonProperty("placementId")
    String placementId;

}

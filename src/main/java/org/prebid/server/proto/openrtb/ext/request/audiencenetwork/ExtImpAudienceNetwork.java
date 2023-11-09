package org.prebid.server.proto.openrtb.ext.request.audiencenetwork;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAudienceNetwork {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("publisherId")
    String publisherId;
}

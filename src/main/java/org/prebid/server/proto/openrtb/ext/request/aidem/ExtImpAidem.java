package org.prebid.server.proto.openrtb.ext.request.aidem;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAidem {

    @JsonProperty("siteId")
    String siteId;

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("rateLimit")
    String rateLimit;
}

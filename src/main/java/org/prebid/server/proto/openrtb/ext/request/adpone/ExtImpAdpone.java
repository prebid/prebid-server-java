package org.prebid.server.proto.openrtb.ext.request.adpone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class ExtImpAdpone {

    @JsonProperty("placementId")
    String placementId;
}

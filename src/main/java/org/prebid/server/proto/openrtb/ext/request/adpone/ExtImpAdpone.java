package org.prebid.server.proto.openrtb.ext.request.adpone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ExtImpAdpone {

    @JsonProperty("placementId")
    String placementId;
}

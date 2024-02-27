package org.prebid.server.proto.openrtb.ext.request.adquery;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdQuery {

    @JsonProperty("placementId")
    String placementId;

    String type;
}

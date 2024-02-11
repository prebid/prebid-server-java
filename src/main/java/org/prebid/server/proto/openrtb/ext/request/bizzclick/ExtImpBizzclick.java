package org.prebid.server.proto.openrtb.ext.request.bizzclick;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBizzclick {

    @JsonProperty("host")
    String host;

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("placementId")
    String placementId;
}

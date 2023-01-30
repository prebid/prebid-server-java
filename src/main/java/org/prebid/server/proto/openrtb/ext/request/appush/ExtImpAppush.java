package org.prebid.server.proto.openrtb.ext.request.appush;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExtImpAppush {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("endpointId")
    String endpointId;
}

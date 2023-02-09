package org.prebid.server.bidder.appush.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AppushImpExtBidder {

    String type;

    @JsonProperty(value = "placementId")
    String placementId;

    @JsonProperty(value = "endpointId")
    String endpointId;
}

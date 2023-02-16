package org.prebid.server.bidder.boldwin.proto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class BoldwinImpExtBidder {

    String type;

    @JsonProperty(value = "placementId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String placementId;

    @JsonProperty(value = "endpointId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String endpointId;
}

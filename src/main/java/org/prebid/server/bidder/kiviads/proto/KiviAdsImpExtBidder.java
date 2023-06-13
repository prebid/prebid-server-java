package org.prebid.server.bidder.kiviads.proto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class KiviAdsImpExtBidder {

    String type;

    @JsonProperty("placementId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String placementId;

    @JsonProperty("endpointId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String endpointId;
}

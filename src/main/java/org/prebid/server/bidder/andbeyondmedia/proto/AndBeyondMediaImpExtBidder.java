package org.prebid.server.bidder.andbeyondmedia.proto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AndBeyondMediaImpExtBidder {

    String type;

    @JsonProperty(value = "placementId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String placementId;
}

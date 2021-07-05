package org.prebid.server.bidder.evolution.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.BidType;

@Value
@AllArgsConstructor(staticName = "of")
public class EvolutionBidResponse {

    @JsonProperty("mediaType")
    BidType mediaType;
}

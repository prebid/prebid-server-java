package org.prebid.server.bidder.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class InvibesBidderResponse {

    String currency;

    @JsonProperty("typedBids")
    List<InvibesTypedBid> typedBids;

    String error;
}

package org.prebid.server.bidder.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class InvibesTypedBid {

    Bid bid;

    @JsonProperty("dealPriority")
    Integer dealPriority;

}

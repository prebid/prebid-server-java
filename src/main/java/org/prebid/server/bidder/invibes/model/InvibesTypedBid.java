package org.prebid.server.bidder.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import com.iab.openrtb.response.Bid;

@Builder
@Value
public class InvibesTypedBid {

    Bid bid;

    @JsonProperty("dealPriority")
    Integer dealPriority;

}

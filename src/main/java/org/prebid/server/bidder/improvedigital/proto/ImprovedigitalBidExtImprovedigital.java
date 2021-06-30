package org.prebid.server.bidder.improvedigital.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ImprovedigitalBidExtImprovedigital {

    @JsonProperty("buying_type")
    String buyingType;

    @JsonProperty("line_item_id")
    Integer lineItemId;
}

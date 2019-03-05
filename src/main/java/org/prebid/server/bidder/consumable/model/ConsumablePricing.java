package org.prebid.server.bidder.consumable.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ConsumablePricing {

    @JsonProperty("clearPrice")
    Double clearPrice;
}

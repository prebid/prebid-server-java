package org.prebid.server.bidder.optout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class CurrencySettings {

    @JsonProperty("adServerCurrency")
    String adServerCurrency;

    @JsonProperty("granularityMultiplier")
    Integer granularityMultiplier;
}

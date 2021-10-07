package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class CurrencyServiceState {

    @JsonProperty("lastUpdate")
    String lastUpdate;
}

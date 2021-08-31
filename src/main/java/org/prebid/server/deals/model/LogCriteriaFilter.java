package org.prebid.server.deals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class LogCriteriaFilter {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("bidderCode")
    String bidderCode;

    @JsonProperty("lineItemId")
    String lineItemId;
}

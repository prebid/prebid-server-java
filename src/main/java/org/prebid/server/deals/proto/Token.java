package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for lineItems[].deliverySchedule[].tokens[].
 */
@AllArgsConstructor(staticName = "of")
@Value
public class Token {

    @JsonProperty("class")
    Integer priorityClass;

    Integer total;
}

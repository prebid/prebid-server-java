package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for lineItems[].deliverySchedule[].tokens[].
 */
@Value(staticConstructor = "of")
public class Token {

    @JsonProperty("class")
    Integer priorityClass;

    Integer total;
}

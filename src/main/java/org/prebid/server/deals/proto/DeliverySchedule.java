package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Defines the contract for lineItems[].deliverySchedules[].
 */
@Builder
@Value
public class DeliverySchedule {

    @JsonProperty("planId")
    String planId;

    @JsonProperty("startTimeStamp")
    ZonedDateTime startTimeStamp;

    @JsonProperty("endTimeStamp")
    ZonedDateTime endTimeStamp;

    @JsonProperty("updatedTimeStamp")
    ZonedDateTime updatedTimeStamp;

    Set<Token> tokens;
}

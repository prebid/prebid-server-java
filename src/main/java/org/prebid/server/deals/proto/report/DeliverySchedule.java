package org.prebid.server.deals.proto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder(toBuilder = true)
@Value
public class DeliverySchedule {

    @JsonProperty("planId")
    String planId;

    @JsonProperty("planStartTimeStamp")
    String planStartTimeStamp;

    @JsonProperty("planExpirationTimeStamp")
    String planExpirationTimeStamp;

    @JsonProperty("planUpdatedTimeStamp")
    String planUpdatedTimeStamp;

    Set<Token> tokens;
}

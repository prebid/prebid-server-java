package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for lineItems[].frequencyCap.
 */
@Builder
@Value
public class FrequencyCap {

    @JsonProperty("fcapId")
    String fcapId;

    Long count;

    Integer periods;

    @JsonProperty("periodType")
    String periodType;
}

package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AccountPriceFloorsConfig {

    Boolean enabled;

    AccountPriceFloorsFetchConfig fetch;

    @JsonProperty("enforce-floors-rate")
    Integer enforceFloorsRate;

    @JsonProperty("adjust-for-bid-adjustment")
    Boolean adjustForBidAdjustment;

    @JsonProperty("enforce-deal-floors")
    Boolean enforceDealFloors;

    @JsonProperty("use-dynamic-data")
    Boolean useDynamicData;
}

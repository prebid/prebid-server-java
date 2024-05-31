package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AccountPriceFloorsConfig {

    Boolean enabled;

    AccountPriceFloorsFetchConfig fetch;

    @JsonAlias("enforce-floors-rate")
    Integer enforceFloorsRate;

    @JsonAlias("adjust-for-bid-adjustment")
    Boolean adjustForBidAdjustment;

    @JsonAlias("enforce-deal-floors")
    Boolean enforceDealFloors;

    @JsonAlias("use-dynamic-data")
    Boolean useDynamicData;
}

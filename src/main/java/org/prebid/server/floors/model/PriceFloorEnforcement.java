package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class PriceFloorEnforcement {

    @JsonProperty("enforceJS")
    Boolean enforceJS;

    @JsonProperty("enforcePBS")
    Boolean enforcePBS;

    @JsonProperty("floorDeals")
    Boolean floorDeals;

    @JsonProperty("bidAdjustment")
    Boolean bidAdjustment;
}

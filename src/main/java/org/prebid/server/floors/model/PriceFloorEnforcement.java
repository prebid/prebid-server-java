package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class PriceFloorEnforcement {

    // prebid server and floors provider fields

    @JsonProperty("enforceJS")
    Boolean enforceJs;

    @JsonProperty("enforcePBS")
    Boolean enforcePbs;

    @JsonProperty("floorDeals")
    Boolean floorDeals;

    @JsonProperty("bidAdjustment")
    Boolean bidAdjustment;

    // prebid server specific fields

    @JsonProperty("enforceRate")
    Integer enforceRate;
}

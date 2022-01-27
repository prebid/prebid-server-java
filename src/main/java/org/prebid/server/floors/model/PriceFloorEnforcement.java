package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
public class PriceFloorEnforcement {

    @JsonProperty("enforceJS")
    Boolean enforceJs;

    @JsonProperty("enforcePBS")
    Boolean enforcePbs;

    @JsonProperty("floorDeals")
    Boolean floorDeals;

    @JsonProperty("bidAdjustment")
    Boolean bidAdjustment;
}

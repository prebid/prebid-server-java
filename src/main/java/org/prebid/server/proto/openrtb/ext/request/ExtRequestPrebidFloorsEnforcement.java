package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.floors.enforcement
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidFloorsEnforcement {

    @JsonProperty("enforceRate")
    Integer enforceRate;

    @JsonProperty("floorDeals")
    Boolean floorDeals;

    @JsonProperty("enforcePBS")
    Boolean enforcePbs;
}

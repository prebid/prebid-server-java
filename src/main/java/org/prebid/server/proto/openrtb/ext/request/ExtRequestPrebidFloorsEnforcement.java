package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.floors.enforcement
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidFloorsEnforcement {

    /**
     * Defines the contract for bidrequest.ext.prebid.floors.enforcement.floorsDisabled
     */
    @JsonProperty("floorsDisabled")
    Boolean floorsDisabled;

    /**
     * Defines the contract for bidrequest.ext.prebid.floors.enforcement.enforcePBS
     */
    @JsonProperty("enforcePBS")
    Boolean enforcePbs;

    /**
     * Defines the contract for bidrequest.ext.prebid.floors.enforcement.floorDeals
     */
    @JsonProperty("floorDeals")
    Boolean floorDeals;

    /**
     * Defines the contract for bidrequest.ext.prebid.floors.enforcement.bidAdjustment
     */
    @JsonProperty("bidAdjustment")
    Boolean bidAdjustment;
}

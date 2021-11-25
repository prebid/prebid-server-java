package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.floors.model.PriceFloorRules;

/**
 * Defines the contract for bidrequest.ext.prebid.floors
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidFloors {

    /**
     * Defines the contract for bidrequest.ext.prebid.floors.enforcement
     */
    ExtRequestPrebidFloorsEnforcement enforcement;

    /**
     * Defines the contract for bidrequest.ext.prebid.floors.data
     */
    PriceFloorRules data;
}

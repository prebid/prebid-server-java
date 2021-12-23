package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorRules;

/**
 * Defines the contract for bidrequest.ext.prebid.floors
 */
@Builder(toBuilder = true)
@Value
public class ExtRequestPrebidFloors {

    Boolean enabled;

    String country;

    // TODO: check if analytic use it as an enum
    PriceFloorLocation location;

    // TODO: should this data be taken from rules.enforcement?
    ExtRequestPrebidFloorsEnforcement enforcement;

    PriceFloorRules rules;
}

package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchStatus;

/**
 * Defines the contract for bidrequest.ext.prebid.floors
 */
@Builder(toBuilder = true)
@Value
public class ExtRequestPrebidFloors {

    Boolean enabled;

    String country;

    @JsonProperty("fetchStatus")
    FetchStatus fetchStatus;

    // TODO: check if analytic use it as an enum
    PriceFloorLocation location;

    // TODO: should this data be taken from rules.enforcement?
    ExtRequestPrebidFloorsEnforcement enforcement;

    PriceFloorRules rules;
}

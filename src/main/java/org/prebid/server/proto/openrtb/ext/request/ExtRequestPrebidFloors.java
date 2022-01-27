package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchStatus;

/**
 * Defines the contract for bidrequest.ext.prebid.floors
 */
@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class ExtRequestPrebidFloors extends PriceFloorRules {

    Boolean enabled;

    @JsonProperty("fetchStatus")
    FetchStatus fetchStatus;

    PriceFloorLocation location;

    Boolean skipped;

    @JsonProperty("floorMinCur")
    String floorMinCur;

    ExtRequestPrebidFloorsEnforcement enforcement; // hides parent field

    public static ExtRequestPrebidFloors empty() {
        return ExtRequestPrebidFloors.builder().build();
    }
}

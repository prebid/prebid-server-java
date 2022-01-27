package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.prebid.server.floors.model.PriceFloorEnforcement;

/**
 * Defines the contract for bidrequest.ext.prebid.floors.enforcement
 */
@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class ExtRequestPrebidFloorsEnforcement extends PriceFloorEnforcement {

    @JsonProperty("enforceRate")
    Integer enforceRate;
}

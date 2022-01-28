package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.floors.proto.FetchStatus;

import java.math.BigDecimal;

/**
 * This model is a trade-off.
 * <p>
 * It defines both:
 * 1. The contract for prebid server bidrequest.ext.prebid.floors field.
 * 2. The contract for floors provider (assuming prebid server specific fields will not be overridden).
 * <p>
 * To make things better, it should be divided in two separate models:
 * for prebid request and floors provider.
 */
@Value
@Builder(toBuilder = true)
public class PriceFloorRules {

    // prebid server and floors provider fields

    @JsonProperty("floorMin")
    BigDecimal floorMin;

    @JsonProperty("floorProvider")
    String floorProvider;

    PriceFloorEnforcement enforcement;

    @JsonProperty("skipRate")
    Integer skipRate;

    PriceFloorEndpoint endpoint;

    PriceFloorData data;

    // prebid server specific fields

    Boolean enabled;

    @JsonProperty("fetchStatus")
    FetchStatus fetchStatus;

    PriceFloorLocation location;

    Boolean skipped;

    @JsonProperty("floorMinCur")
    String floorMinCur;
}

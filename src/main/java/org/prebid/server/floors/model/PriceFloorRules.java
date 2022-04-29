package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.floors.proto.FetchStatus;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class PriceFloorRules {

    @JsonProperty("floorMin")
    BigDecimal floorMin;

    @JsonProperty("floorProvider")
    String floorProvider;

    PriceFloorEnforcement enforcement;

    @JsonProperty("skipRate")
    Integer skipRate;

    PriceFloorEndpoint endpoint;

    PriceFloorData data;

    Boolean enabled;

    @JsonProperty("fetchStatus")
    FetchStatus fetchStatus;

    PriceFloorLocation location;

    Boolean skipped;

    @JsonProperty("floorMinCur")
    String floorMinCur;
}

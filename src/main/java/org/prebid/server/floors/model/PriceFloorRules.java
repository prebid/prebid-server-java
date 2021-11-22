package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value
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
}

package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode
@NoArgsConstructor
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

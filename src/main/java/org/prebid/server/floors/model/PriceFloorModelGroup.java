package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
public class PriceFloorModelGroup {

    String currency;

    @JsonProperty("skipRate")
    Integer skipRate;

    @JsonProperty("modelVersion")
    String modelVersion;

    @JsonProperty("modelWeight")
    Integer modelWeight;

    PriceFloorSchema schema;

    Map<String, BigDecimal> values;

    @JsonProperty("default")
    BigDecimal defaultFloor;
}

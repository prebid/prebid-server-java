package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Value
public class PriceFloorData {

    @JsonProperty("floorProvider")
    String floorProvider;

    String currency;

    @JsonProperty("skipRate")
    Integer skipRate;

    @JsonProperty("floorsSchemaVersion")
    String floorsSchemaVersion;

    @JsonProperty("modelTimestamp")
    Integer modelTimestamp;

    @JsonProperty("modelGroups")
    List<PriceFloorModelGroup> modelGroups;

    PriceFloorSchema schema;

    Map<String, BigDecimal> values;
}

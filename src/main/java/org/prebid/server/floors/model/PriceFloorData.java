package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class PriceFloorData {

    @JsonProperty("floorProvider")
    String floorProvider;

    String currency;

    @JsonProperty("skipRate")
    Integer skipRate;

    @JsonProperty("useFetchDataRate")
    Integer useFetchDataRate;

    @JsonProperty("floorsSchemaVersion")
    String floorsSchemaVersion;

    @JsonProperty("modelTimestamp")
    Long modelTimestamp;

    @JsonProperty("modelGroups")
    List<PriceFloorModelGroup> modelGroups;

    @JsonProperty("noFloorSignalBidders")
    List<String> noFloorSignalBidders;
}

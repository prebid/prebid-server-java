package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidrequest.ext.prebid.floors
 */
@Builder
@Value
public class ExtRequestPrebidFloors {

    Boolean enabled;

    @JsonProperty("floorCurrency")
    String floorCurrency;

    @JsonProperty("floorProvider")
    String floorProvider;

    @JsonProperty("floorMin")
    BigDecimal floorMin;

    String location;

    @JsonProperty("modelVersion")
    String modelVersion;

    @JsonProperty("modelWeight")
    Integer modelWeight;

    @JsonProperty("modelTimestamp")
    Long modelTimestamp;

    @JsonProperty("skipRate")
    Integer skipRate;

    Boolean skipped;

    String country;

    ExtRequestPrebidFloorsEnforcement enforcement;
}

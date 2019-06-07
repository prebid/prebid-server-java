package org.prebid.server.proto.openrtb.ext.request.openx;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Defines the contract for bidrequest.imp[i].ext.openx
 */
@Builder
@Value
public class ExtImpOpenx {
    String unit;

    @JsonProperty("delDomain")
    String delDomain;

    @JsonProperty("customFloor")
    BigDecimal customFloor;

    @JsonProperty("customParams")
    Map<String, JsonNode> customParams;
}

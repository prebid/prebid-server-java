package org.prebid.server.proto.openrtb.ext.request.openx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

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
    Float customFloor;

    @JsonProperty("customParams")
    Map<String, String> customParams;
}

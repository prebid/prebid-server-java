package org.prebid.server.proto.openrtb.ext.request.yieldlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Defines the contract for bidrequest.imp[i].ext.yieldlab
 */
@Builder
@Value
public class ExtImpYieldlab {

    @JsonProperty("adslotId")
    String adslotId;

    @JsonProperty("supplyId")
    String supplyId;

    @JsonProperty("adSize")
    String adSize;

    Map<String, String> targeting;

    @JsonProperty("extId")
    String extId;
}

package org.prebid.server.proto.openrtb.ext.request.adkernel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adkernel
 */
@Builder
@Value
public class ExtImpAdkernel {

    @JsonProperty("zoneId")
    Integer zoneId;

    String host;
}

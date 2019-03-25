package org.prebid.server.proto.openrtb.ext.request.yieldmo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.yieldmo
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYieldmo {

    @JsonProperty("placementId")
    String placementId;
}

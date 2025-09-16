package org.prebid.server.proto.openrtb.ext.request.yieldmo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.yieldmo
 */
@Value(staticConstructor = "of")
public class ExtImpYieldmo {

    @JsonProperty("placementId")
    String placementId;
}

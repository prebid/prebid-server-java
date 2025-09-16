package org.prebid.server.proto.openrtb.ext.request.yieldone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.yieldone
 */
@Value(staticConstructor = "of")
public class ExtImpYieldone {

    @JsonProperty("placementId")
    String placementId;
}

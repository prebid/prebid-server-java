package org.prebid.server.proto.openrtb.ext.request.decenterads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.decenterads
 */
@Value(staticConstructor = "of")
public class ExtImpDecenterads {

    @JsonProperty("placementId")
    String placementId;
}

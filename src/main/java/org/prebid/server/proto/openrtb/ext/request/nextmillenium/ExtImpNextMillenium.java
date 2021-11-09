package org.prebid.server.proto.openrtb.ext.request.nextmillenium;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.nextmillenium
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpNextMillenium {

    @JsonProperty("placement_id")
    String placementId;
}

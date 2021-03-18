package org.prebid.server.proto.openrtb.ext.request.adyoulike;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.adyoulike
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdyoulike {

    @JsonProperty("placement")
    String placementId;

    String campaign;

    String track;

    String creative;

    String source;

    String debug;
}

package org.prebid.server.proto.openrtb.ext.request.displayio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class DisplayioImpExt {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("inventoryId")
    String inventoryId;

    @JsonProperty("placementId")
    String placementId;

}

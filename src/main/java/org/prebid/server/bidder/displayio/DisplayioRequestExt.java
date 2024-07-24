package org.prebid.server.bidder.displayio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class DisplayioRequestExt {

    @JsonProperty("inventoryId")
    String inventoryId;

    @JsonProperty("placementId")
    String placementId;
}

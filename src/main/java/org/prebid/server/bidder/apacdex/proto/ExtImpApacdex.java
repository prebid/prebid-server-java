package org.prebid.server.bidder.apacdex.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpApacdex {

    @JsonProperty(value = "placementId")
    String placementId;

    @JsonProperty(value = "siteId")
    String siteId;

    @JsonProperty(value = "floorPrice")
    Float floorPrice;

}

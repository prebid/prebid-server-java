package org.prebid.server.bidder.aja.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAja {

    @JsonProperty("asi")
    String adSpotID;
}

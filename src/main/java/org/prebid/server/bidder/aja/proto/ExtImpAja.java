package org.prebid.server.bidder.aja.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAja {

    @JsonProperty("asi")
    String adSpotID;
}

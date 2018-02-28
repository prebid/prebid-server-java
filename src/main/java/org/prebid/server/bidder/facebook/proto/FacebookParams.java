package org.prebid.server.bidder.facebook.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class FacebookParams {

    @JsonProperty("placementId")
    String placementId;
}

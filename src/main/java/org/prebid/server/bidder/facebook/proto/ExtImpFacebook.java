package org.prebid.server.bidder.facebook.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpFacebook {

    @JsonProperty("placementId")
    String placementId;
}

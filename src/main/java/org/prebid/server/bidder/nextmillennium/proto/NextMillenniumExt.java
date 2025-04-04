package org.prebid.server.bidder.nextmillennium.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class NextMillenniumExt {

    @JsonProperty("nextMillennium")
    NextMillenniumExtBidder nextMillennium;
}

package org.prebid.server.bidder.amx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AmxBidExt {

    @JsonProperty("ct")
    Integer creativeType;

    @JsonProperty("startdelay")
    Integer startDelay;

    @JsonProperty("ds")
    String demandSource;

    @JsonProperty("bc")
    String bidderCode;

    public static AmxBidExt empty() {
        return AmxBidExt.of(null, null, null, null);
    }
}

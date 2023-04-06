package org.prebid.server.bidder.amx.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AmxBidExt {

    @JsonProperty("ct")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer creativeType;

    @JsonProperty("startdelay")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer startDelay;
}

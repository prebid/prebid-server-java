package org.prebid.server.bidder.amx.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class AmxBidExt {

    @JsonProperty("himp")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<String> himp;

    @JsonProperty("startdelay")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String startDelay;
}

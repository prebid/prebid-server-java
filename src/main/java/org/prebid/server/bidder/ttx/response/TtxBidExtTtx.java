package org.prebid.server.bidder.ttx.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class TtxBidExtTtx {

    @JsonProperty("mediaType")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String mediaType;
}

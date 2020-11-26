package org.prebid.server.bidder.ttx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class TtxBidExt {

    @JsonProperty("ttx")
    TtxBidExtTtx ttx;
}

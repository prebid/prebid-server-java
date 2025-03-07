package org.prebid.server.bidder.rubicon.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconExtPrebidBiddersBidder {

    String integration;

    RubiconExtPrebidBiddersBidderDebug debug;

    @JsonProperty("apexRenderer")
    Boolean apexRenderer;
}

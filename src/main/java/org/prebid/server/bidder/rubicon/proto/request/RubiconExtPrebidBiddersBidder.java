package org.prebid.server.bidder.rubicon.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconExtPrebidBiddersBidder {

    String integration;

    RubiconExtPrebidBiddersBidderDebug debug;

    @JsonProperty("apexRenderer")
    Boolean apexRenderer;
}

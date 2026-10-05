package org.prebid.server.bidder.magnite.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class MagniteExtPrebidBiddersBidder {

    String integration;

    MagniteExtPrebidBiddersBidderDebug debug;

    @JsonProperty("apexRenderer")
    Boolean apexRenderer;
}

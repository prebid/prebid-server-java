package org.prebid.server.bidder.nexx360;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Nexx360ExtBid {

    @JsonProperty("bidType")
    String bidType;
}

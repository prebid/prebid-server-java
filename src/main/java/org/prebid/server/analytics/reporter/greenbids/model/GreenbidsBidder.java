package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class GreenbidsBidder {

    @JsonProperty("bidder")
    String bidder;
    @JsonProperty("isTimeout")
    Boolean isTimeout;
    @JsonProperty("hasBid")
    Boolean hasBid;
}

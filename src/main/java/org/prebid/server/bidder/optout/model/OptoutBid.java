package org.prebid.server.bidder.optout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value(staticConstructor = "of")
public class OptoutBid {

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("bidId")
    String bidId;

    BigDecimal cpm;

    String currency;

    Integer width;

    Integer height;

    String ad;

    Integer ttl;

    @JsonProperty("creativeId")
    String creativeId;

    @JsonProperty("optOutExt")
    ObjectNode optOutExt;

    String meta;
}

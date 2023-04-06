package org.prebid.server.bidder.stroeercore.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class StroeerCoreBid {

    String id;

    @JsonProperty("bidId")
    String bidId;

    BigDecimal cpm;

    Integer width;

    Integer height;

    @JsonProperty("ad")
    String adMarkup;

    @JsonProperty("crid")
    String creativeId;
}


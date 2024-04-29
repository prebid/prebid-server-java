package org.prebid.server.bidder.stroeercore.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class StroeerCoreBid {

    String id;

    @JsonProperty("bidId")
    String impId;

    BigDecimal cpm;

    Integer width;

    Integer height;

    @JsonProperty("ad")
    String adMarkup;

    @JsonProperty("crid")
    String creativeId;

    ObjectNode dsa;
}


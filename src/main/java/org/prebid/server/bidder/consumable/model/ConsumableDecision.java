package org.prebid.server.bidder.consumable.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ConsumableDecision {

    ConsumablePricing pricing;

    @JsonProperty("adId")
    Long adId;

    @JsonProperty("bidderName")
    String bidderName;

    @JsonProperty("creativeId")
    String creativeId;

    List<ConsumableContents> contents;

    @JsonProperty("impressionUrl")
    String impressionUrl;

    Integer width;

    Integer height;
}

package org.prebid.server.bidder.criteo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class CriteoRequestSlot {

    @JsonProperty("slotid")
    String slotId;

    @JsonProperty("impid")
    String impId;

    @JsonProperty("zoneid")
    Integer zoneId;

    @JsonProperty("networkid")
    Integer networkId;

    List<String> sizes;
}

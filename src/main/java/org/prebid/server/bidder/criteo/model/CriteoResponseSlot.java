package org.prebid.server.bidder.criteo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value
public class CriteoResponseSlot {

    String id;

    @JsonProperty("impid")
    String impId;

    @JsonProperty("zoneid")
    Integer zoneId;

    @JsonProperty("networkid")
    Integer networkId;

    BigDecimal cpm;

    String currency;

    Integer width;

    Integer height;

    String creative;

    @JsonProperty("creativeid")
    String creativeId;
}

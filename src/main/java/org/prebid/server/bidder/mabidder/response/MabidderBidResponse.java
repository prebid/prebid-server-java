package org.prebid.server.bidder.mabidder.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class MabidderBidResponse {

    @JsonProperty("requestId")
    String requestId;

    String currency;

    Integer width;

    Integer height;

    @JsonProperty("creativeId")
    String creativeId;

    @JsonProperty("dealId")
    String dealId;

    @JsonProperty("netRevenue")
    Boolean netRevenue;

    Integer ttl;

    String ad;

    @JsonProperty("mediaType")
    String mediaType;

    Meta meta;

    BigDecimal cpm;

}

package org.prebid.server.bidder.adform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value
public class AdformResponse {

    @JsonProperty("response")
    String responseType;

    String banner;

    @JsonProperty("win_bid")
    BigDecimal price;

    @JsonProperty("win_cur")
    String currency;

    Integer width;

    Integer height;

    String dealId;
}

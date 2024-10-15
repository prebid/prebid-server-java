package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value
public class MissenaAdResponse {

    String ad;

    BigDecimal cpm;

    String currency;

    @JsonProperty("requestId")
    String requestId;
}

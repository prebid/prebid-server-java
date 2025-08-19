package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class MissenaAdRequest {

    @JsonProperty("adunit")
    String adUnit;

    String currency;

    BigDecimal floor;

    String floorCurrency;

    @JsonProperty("ik")
    String idempotencyKey;

    String requestId;

    Long timeout;

    MissenaUserParams params;

    @JsonProperty("ortb2")
    BidRequest bidRequest;

    String version;
}

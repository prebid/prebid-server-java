package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class GreenbidsBid {

    String bidder;

    @JsonProperty("isTimeout")
    Boolean isTimeout;

    @JsonProperty("hasBid")
    Boolean hasBid;

    JsonNode params;

    BigDecimal cpm;

    String currency;

    public static GreenbidsBid ofBid(String seat, Bid bid, JsonNode params, String currency) {
        return GreenbidsBid.builder()
                .bidder(seat)
                .isTimeout(false)
                .hasBid(bid != null)
                .params(params)
                .cpm(bid.getPrice())
                .currency(currency)
                .build();
    }

    public static GreenbidsBid ofNonBid(String seat, NonBid nonBid, JsonNode params, String currency) {
        return GreenbidsBid.builder()
                .bidder(seat)
                .isTimeout(nonBid.getStatusCode() == BidRejectionReason.ERROR_TIMED_OUT)
                .hasBid(false)
                .params(params)
                .currency(currency)
                .build();
    }
}

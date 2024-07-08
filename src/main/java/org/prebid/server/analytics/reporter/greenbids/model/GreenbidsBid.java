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

    public static GreenbidsBidBuilder ofBidBuilder(String seat, Bid bid) {
        return GreenbidsBid.builder()
                .bidder(seat)
                .isTimeout(false)
                .hasBid(bid != null)
                .cpm(bid.getPrice());
    }

    public static GreenbidsBidBuilder ofNonBidBuilder(String seat, NonBid nonBid) {
        return GreenbidsBid.builder()
                .bidder(seat)
                .isTimeout(nonBid.getStatusCode() == BidRejectionReason.TIMED_OUT)
                .hasBid(false);
    }
}

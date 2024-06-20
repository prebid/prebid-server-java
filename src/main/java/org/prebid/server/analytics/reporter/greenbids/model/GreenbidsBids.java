package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class GreenbidsBids {

    String bidder;

    Boolean isTimeout;

    Boolean hasBid;

    JsonNode params;

    BigDecimal cpm;

    String currency;

    public static GreenbidsBids ofBid(String seat, Bid bid) {
        return GreenbidsBids.builder()
                .bidder(seat)
                .isTimeout(false)
                .hasBid(bid != null)
                .cpm(bid.getPrice())
                .build();
    }

    public static GreenbidsBids ofNonBid(String seat, NonBid nonBid) {
        return GreenbidsBids.builder()
                .bidder(seat)
                .isTimeout(nonBid.getStatusCode() == BidRejectionReason.TIMED_OUT)
                .hasBid(false)
                .build();
    }
}

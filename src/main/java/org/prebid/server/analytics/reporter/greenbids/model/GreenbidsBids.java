package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;

@Builder(toBuilder = true)
@Value
public class GreenbidsBids {

    String bidder;

    @JsonProperty("isTimeout")
    Boolean isTimeout;

    @JsonProperty("hasBid")
    Boolean hasBid;

    public static GreenbidsBids ofBid(String seat, Bid bid) {
        return GreenbidsBids.builder()
                .bidder(seat)
                .isTimeout(false)
                .hasBid(bid != null)
                .build();
    }

    public static GreenbidsBids ofNonBid(String seat, NonBid nonBid) {
        return GreenbidsBids.builder()
                .bidder(seat)
                .isTimeout(nonBid.getStatusCode() == BidRejectionReason.ERROR_TIMED_OUT)
                .hasBid(false)
                .build();
    }
}

package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.model.BidderSeatBid;

/**
 * Structure to pass {@link BidderSeatBid} along with bidder name and extra tracking data generated during bidding
 */
@AllArgsConstructor(staticName = "of")
@Value
public class BidderResponse {

    String bidder;

    BidderSeatBid seatBid;

    int responseTime;

    public BidderResponse with(BidderSeatBid seatBid) {
        return BidderResponse.of(this.bidder, seatBid, this.responseTime);
    }
}

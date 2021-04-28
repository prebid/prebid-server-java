package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.model.BidderInfoSeatBid;
import org.prebid.server.bidder.model.BidderSeatBid;

/**
 * Structure to pass {@link BidderSeatBid} along with bidder name and extra tracking data generated during bidding
 */
@AllArgsConstructor(staticName = "of")
@Value
public class BidderInfoResponse {

    String bidder;

    BidderInfoSeatBid seatBid;

    int responseTime;

    public BidderInfoResponse with(BidderInfoSeatBid seatBid) {
        return of(this.bidder, seatBid, this.responseTime);
    }
}

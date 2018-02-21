package org.rtb.vexing.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.rtb.vexing.bidder.model.BidderSeatBid;

/**
 * Structure to pass {@link BidderSeatBid} along with bidder name and extra tracking data generated during bidding
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class BidderResponse {

    String bidder;

    BidderSeatBid seatBid;

    int responseTime;
}

package org.rtb.vexing.auction.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.bidder.model.BidderSeatBid;

/**
 * Structure to pass {@link BidderSeatBid} along with bidder name and extra tracking data generated during bidding
 */
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class BidderResponse {

    String bidder;

    BidderSeatBid seatBid;

    int responseTime;
}

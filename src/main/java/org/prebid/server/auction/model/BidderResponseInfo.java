package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.model.BidderSeatBidInfo;

@AllArgsConstructor(staticName = "of")
@Value
public class BidderResponseInfo {

    String bidder;

    BidderSeatBidInfo seatBid;

    Integer nbr;

    int responseTime;

    public BidderResponseInfo with(BidderSeatBidInfo seatBid) {
        return of(this.bidder, seatBid, this.nbr, this.responseTime);
    }
}

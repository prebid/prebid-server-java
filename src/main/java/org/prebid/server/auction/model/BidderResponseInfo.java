package org.prebid.server.auction.model;

import lombok.Value;
import org.prebid.server.bidder.model.BidderSeatBidInfo;

@Value(staticConstructor = "of")
public class BidderResponseInfo {

    String bidder;

    String seat;

    String adapterCode;

    BidderSeatBidInfo seatBid;

    int responseTime;

    public BidderResponseInfo with(BidderSeatBidInfo seatBid) {
        return of(this.bidder, this.seat, this.adapterCode, seatBid, this.responseTime);
    }
}

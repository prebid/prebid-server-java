package org.prebid.server.auction.model;

import lombok.Value;
import org.prebid.server.bidder.model.BidderBid;

@Value(staticConstructor = "of")
public class RejectedBid implements Rejected {

    BidderBid bid;

    BidRejectionReason reason;

    @Override
    public String impId() {
        return bid.getBid().getImpid();
    }

    @Override
    public BidRejectionReason reason() {
        return reason;
    }

    public String bidId() {
        return bid.getBid().getId();
    }
}

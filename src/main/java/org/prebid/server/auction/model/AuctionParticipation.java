package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Data;

/**
 * Representation of a single auction interaction
 */
@Builder
@Data
public class AuctionParticipation {

    String bidder;

    // Will be null when requestBlocked
    BidderRequest bidderRequest;

    // Will be null when requestBlocked
    BidderResponse bidderResponse;

    boolean requestBlocked;

    boolean analyticsBlocked;

    public AuctionParticipation insertBidderResponse(BidderResponse bidderResponse) {
        this.bidderResponse = bidderResponse;
        return this;
    }
}

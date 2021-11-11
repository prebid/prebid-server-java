package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;

/**
 * Representation of a single auction interaction
 */
@Builder(toBuilder = true)
@Value
public class AuctionParticipation {

    String bidder;

    // Will be null when requestBlocked
    BidderRequest bidderRequest;

    // Will be null when requestBlocked
    BidderResponse bidderResponse;

    boolean requestBlocked;

    boolean analyticsBlocked;

    public AuctionParticipation with(BidderResponse bidderResponse) {
        return this.toBuilder().bidderResponse(bidderResponse).build();
    }
}

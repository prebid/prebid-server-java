package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;

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

    Map<String, RejectionReason> rejectedImpIds;

    boolean requestBlocked;

    boolean analyticsBlocked;

    public AuctionParticipation with(BidderResponse bidderResponse) {
        return this.toBuilder().bidderResponse(bidderResponse).build();
    }

    public AuctionParticipation with(Map<String, RejectionReason> rejectedImpIds) {
        return this.toBuilder().rejectedImpIds(new HashMap<>(rejectedImpIds)).build();
    }
}

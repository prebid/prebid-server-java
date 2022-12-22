package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.MapUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of a single auction interaction
 */
@Value
@Builder(toBuilder = true)
public class AuctionParticipation {

    String bidder;

    // Will be null when requestBlocked
    BidderRequest bidderRequest;

    // Will be null when requestBlocked
    BidderResponse bidderResponse;

    @Builder.Default
    Map<String, ImpRejectionReason> rejectedImpIds = new HashMap<>();

    boolean requestBlocked;

    boolean analyticsBlocked;

    public AuctionParticipation with(BidderResponse bidderResponse) {
        return this.toBuilder().bidderResponse(bidderResponse).build();
    }

    public AuctionParticipation with(Map<String, ImpRejectionReason> rejectedImpIds) {
        return this.toBuilder()
                .rejectedImpIds(MapUtil.merge(rejectedImpIds, this.rejectedImpIds))
                .build();
    }
}

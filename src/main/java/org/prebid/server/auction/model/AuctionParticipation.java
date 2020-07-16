package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Data;

/**
 * Structure to pass {@link BidRequest} along with the bidder name
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




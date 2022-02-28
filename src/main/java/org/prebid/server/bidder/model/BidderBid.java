package org.prebid.server.bidder.model;

import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

/**
 * Bid returned by a {@link Bidder}.
 */
@Builder(toBuilder = true)
@Value
public class BidderBid {

    /**
     * bid.ext will become "response.seatbid[i].bid.ext.bidder" in the final OpenRTB response
     */
    Bid bid;

    /**
     * This will become response.seatbid[i].bid.ext.prebid.type" in the final OpenRTB response
     */
    BidType type;

    /**
     * Will be used for converting to ad server currency
     */
    String bidCurrency;

    /**
     * Optionally provided by adapters and used internally to support deal targeted campaigns.
     */
    Integer dealPriority;

    /**
     * Will become response.seatbid[i].bid.ext.prebid.video in the final OpenRTB response.
     */
    ExtBidPrebidVideo videoInfo;

    public static BidderBid of(Bid bid, BidType bidType, String bidCurrency) {
        return BidderBid.builder()
                .bid(bid)
                .type(bidType)
                .bidCurrency(bidCurrency)
                .build();
    }
}

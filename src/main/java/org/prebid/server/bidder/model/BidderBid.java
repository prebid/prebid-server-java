package org.prebid.server.bidder.model;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

/**
 * Bid returned by a {@link Bidder}.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class BidderBid {

    public static BidderBid of(Bid bid, BidType bidType, String bidCurrency) {
        return BidderBid.of(bid, bidType, bidCurrency, null, null);
    }

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

    public BidderBid with(Bid bid) {
        return BidderBid.of(bid, this.type, this.bidCurrency);
    }
}

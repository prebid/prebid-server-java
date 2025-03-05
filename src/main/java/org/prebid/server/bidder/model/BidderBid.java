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
     * bid.ext will become response.seatbid[i].bid.ext.bidder in the final OpenRTB response.
     */
    Bid bid;

    /**
     * Will become response.seatbid[i].bid.ext.prebid.type in the final OpenRTB response.
     */
    BidType type;

    /**
     * Will be used for converting to ad server currency.
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

    /**
     * Will be used by price floor enforcement. The only bidder is responsible to populate this info
     * if bidder overrides any of request.imp[i].bidfloor or request.imp[i].bidfloorcur fields.
     */
    PriceFloorInfo priceFloorInfo;

    /**
     * The seat, which will override the default seat (e.g. the bidder name)
     * if alternate-bidder-codes (ext.prebid.alternatebiddercodes) are allowed
     */
    String seat;

    public static BidderBid of(Bid bid, BidType bidType, String bidCurrency, String seat) {
        return BidderBid.builder()
                .bid(bid)
                .type(bidType)
                .bidCurrency(bidCurrency)
                .seat(seat)
                .build();
    }

    public static BidderBid of(Bid bid, BidType bidType, String bidCurrency) {
        return of(bid, bidType, bidCurrency, null);
    }
}

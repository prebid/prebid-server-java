package org.prebid.server.bidder.model;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;

/**
 * Bid returned by a {@link Bidder}.
 */
@AllArgsConstructor(staticName = "of")
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
     * Will be used for storing original bid cpm
     */
    BigDecimal origbidcpm;

    private BidderBid(Bid bid, BidType type, String bidCurrency) {
        this.bid = bid;
        this.type = type;
        this.bidCurrency = bidCurrency;
        this.origbidcpm = bid != null ? bid.getPrice() : null;
    }

    public static BidderBid of(Bid bid, BidType type, String bidCurrency) {
        return new BidderBid(bid, type, bidCurrency);
    }
}

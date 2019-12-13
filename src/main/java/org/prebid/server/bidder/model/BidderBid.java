package org.prebid.server.bidder.model;

import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidVideo;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.proto.openrtb.ext.response.BidType;

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
     * This will become response.seatbid[i].bid.ext.prebid.type" in the final OpenRTB response
     */
    BidVideo bidVideo;

    /**
     * Will be used for converting to ad server currency
     */
    String bidCurrency;
}

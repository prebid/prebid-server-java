package org.rtb.vexing.bidder.model;

import com.iab.openrtb.response.Bid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.model.openrtb.ext.response.BidType;

/**
 * Bid returned by a {@link Bidder}.
 * <p>
 * This is distinct from the {@link com.iab.openrtb.response.SeatBid} so that the prebid-server ext can be passed
 * back with type safety.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class BidderBid {

    Bid bid;

    /**
     * This will become response.seatbid[i].bid.ext.prebid.type" in the final OpenRTB response
     */
    BidType type;
}

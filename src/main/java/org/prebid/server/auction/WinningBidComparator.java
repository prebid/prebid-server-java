package org.prebid.server.auction;

import com.iab.openrtb.request.Imp;
import org.prebid.server.auction.model.BidInfo;

import java.util.Comparator;
import java.util.Objects;

/**
 * Compares two {@link BidInfo} arguments for order.
 * <p>
 * Returns a negative integer when first is less valuable than second
 * Zero when arguments are equal by their winning value
 * Positive integer when first have more value then second
 */
public class WinningBidComparator implements Comparator<BidInfo> {

    private final Comparator<BidInfo> priceComparator = Comparator.comparing(o -> o.getBid().getPrice());

    @Override
    public int compare(BidInfo bidInfo1, BidInfo bidInfo2) {
        final Imp imp = bidInfo1.getCorrespondingImp();
        // this should never happen
        if (!Objects.equals(imp, bidInfo2.getCorrespondingImp())) {
            throw new IllegalStateException(
                    String.format("Error while determining winning bid: "
                            + "Multiple bids was found for impId: %s", imp.getId()));
        }

        return priceComparator.compare(bidInfo1, bidInfo2);
    }
}

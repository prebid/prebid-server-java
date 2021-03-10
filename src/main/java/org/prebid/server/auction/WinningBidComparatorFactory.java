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
 *
 * <p>
 * The priority for choosing the 'winner' (hb_pb, hb_bidder, etc) is:
 * <p>
 * - Deals bid always wins over bids without deals
 * - Amongst deals bids, choose the highest CPM
 */
public class WinningBidComparatorFactory {

    private static Comparator<BidInfo> winningBidPriceComparator = new WinningBidPriceComparator();
    private static Comparator<BidInfo> winningDealBidComparator = new WinningBidDealComparator();
    private static Comparator<BidInfo> dealsThenPriceComparator = winningDealBidComparator
            .thenComparing(winningBidPriceComparator);

    public Comparator<BidInfo> create(boolean preferDeals) {
        return preferDeals
                ? dealsThenPriceComparator
                : winningBidPriceComparator;
    }

    private static class WinningBidPriceComparator implements Comparator<BidInfo> {

        private static Comparator<BidInfo> priceComparator = Comparator.comparing(o -> o.getBid().getPrice());

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

    private static class WinningBidDealComparator implements Comparator<BidInfo> {

        @Override
        public int compare(BidInfo bidInfo1, BidInfo bidInfo2) {
            final boolean isPresentBidDealId1 = bidInfo1.getBid().getDealid() != null;
            final boolean isPresentBidDealId2 = bidInfo2.getBid().getDealid() != null;

            if (!Boolean.logicalXor(isPresentBidDealId1, isPresentBidDealId2)) {
                return 0;
            }

            return isPresentBidDealId1 ? 1 : -1;
        }
    }
}

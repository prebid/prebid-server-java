package org.prebid.server.auction;

import com.iab.openrtb.request.Imp;
import org.prebid.server.auction.model.BidInfo;

import java.util.Comparator;
import java.util.Objects;

/**
 * Creates {@link Comparator} that compares two {@link BidInfo} arguments for order.
 */
public class WinningBidComparatorFactory {

    private static final Comparator<BidInfo> WINNING_BID_PRICE_COMPARATOR = new WinningBidPriceComparator();
    private static final Comparator<BidInfo> WINNING_BID_DEAL_COMPARATOR = new WinningBidDealComparator();
    private static final Comparator<BidInfo> BID_INFO_COMPARATOR = WINNING_BID_DEAL_COMPARATOR
            .thenComparing(WINNING_BID_PRICE_COMPARATOR);

    public Comparator<BidInfo> create(boolean preferDeals) {
        return preferDeals
                ? BID_INFO_COMPARATOR
                : WINNING_BID_PRICE_COMPARATOR;
    }

    /**
     * Compares two {@link BidInfo} arguments for order based on dealId presence.
     * Returns negative integer when first does not have a deal and second has.
     * Return positive integer when first has deal and second does not
     * Returns zero when both have deals, or both don't have a deal
     */
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

    /**
     * Compares two {@link BidInfo} arguments for order based on price.
     * Returns negative integer when first has lower price.
     * Returns positive integer when first has higher price.
     * Returns zero when both have equal price.
     */
    private static class WinningBidPriceComparator implements Comparator<BidInfo> {

        private static final Comparator<BidInfo> PRICE_COMPARATOR = Comparator.comparing(o -> o.getBid().getPrice());

        @Override
        public int compare(BidInfo bidInfo1, BidInfo bidInfo2) {
            final Imp imp = bidInfo1.getCorrespondingImp();
            // this should never happen
            if (!Objects.equals(imp, bidInfo2.getCorrespondingImp())) {
                throw new IllegalStateException(
                        String.format("Error while determining winning bid: "
                                + "Multiple bids was found for impId: %s", imp.getId()));
            }

            return PRICE_COMPARATOR.compare(bidInfo1, bidInfo2);
        }
    }
}

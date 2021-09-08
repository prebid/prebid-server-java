package org.prebid.server.auction;

import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.BidInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Creates {@link Comparator} that compares two {@link BidInfo} arguments for order.
 */
public class WinningBidComparatorFactory {

    private static final Comparator<BidInfo> WINNING_BID_PRICE_COMPARATOR = new WinningBidPriceComparator();
    private static final Comparator<BidInfo> WINNING_BID_DEAL_COMPARATOR = new WinningBidDealComparator();
    private static final Comparator<BidInfo> WINNING_BID_PG_COMPARATOR = new WinningBidPgComparator();

    private static final Comparator<BidInfo> BID_INFO_COMPARATOR = WINNING_BID_PG_COMPARATOR
            .thenComparing(WINNING_BID_DEAL_COMPARATOR)
            .thenComparing(WINNING_BID_PRICE_COMPARATOR);

    private static final Comparator<BidInfo> PREFER_PRICE_COMPARATOR = WINNING_BID_PG_COMPARATOR
            .thenComparing(WINNING_BID_PRICE_COMPARATOR);

    public Comparator<BidInfo> create(boolean preferDeals) {
        return preferDeals
                ? BID_INFO_COMPARATOR
                : PREFER_PRICE_COMPARATOR;
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
     * Compares two {@link BidInfo} arguments for order based on PG deal priority.
     * Returns negative integer when first does not have a pg deal and second has, or when both have a pg deal,
     * but first has higher index in deals array that means lower priority.
     * Returns positive integer when first has a pg deal and second does not, or when both have a pg deal,
     * but first has lower index in deals array that means higher priority.
     * Returns zero when both dont have pg deals.
     */
    private static class WinningBidPgComparator implements Comparator<BidInfo> {

        private final Comparator<Integer> dealIndexComparator = Comparator.comparingInt(Integer::intValue).reversed();

        @Override
        public int compare(BidInfo bidInfo1, BidInfo bidInfo2) {
            final Imp imp = bidInfo1.getCorrespondingImp();
            final Pmp pmp = imp.getPmp();
            final List<Deal> impDeals = pmp != null ? pmp.getDeals() : null;

            if (CollectionUtils.isEmpty(impDeals)) {
                return 0;
            }

            final Bid bid1 = bidInfo1.getBid();
            final Bid bid2 = bidInfo2.getBid();

            int indexOfBidDealId1 = -1;
            int indexOfBidDealId2 = -1;

            // search for indexes of deals
            for (int i = 0; i < impDeals.size(); i++) {
                final String dealId = impDeals.get(i).getId();
                if (Objects.equals(dealId, bid1.getDealid())) {
                    indexOfBidDealId1 = i;
                }
                if (Objects.equals(dealId, bid2.getDealid())) {
                    indexOfBidDealId2 = i;
                }
            }

            final boolean isPresentImpDealId1 = indexOfBidDealId1 != -1;
            final boolean isPresentImpDealId2 = indexOfBidDealId2 != -1;

            final boolean isOneOrBothDealIdNotPresent = !isPresentImpDealId1 || !isPresentImpDealId2;
            return isOneOrBothDealIdNotPresent
                    ? isPresentImpDealId1 ? 1 : -1 // case when no deal IDs found is covered by response validator
                    : dealIndexComparator.compare(indexOfBidDealId1, indexOfBidDealId2);
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

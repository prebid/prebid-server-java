package org.prebid.server.auction;

import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class for removing bids from response for the same bidder-imp pair.
 */
public class BidResponseReducer {

    /**
     * Removes {@link Bid}s with the same impId taking into account if {@link Bid} has deal.
     * <p>
     * Returns given list of {@link BidderResponse}s if {@link Bid}s have different impIds.
     */
    public BidderResponse removeRedundantBids(BidderResponse bidderResponse) {
        final List<BidderBid> bidderBids = ListUtils.emptyIfNull(bidderResponse.getSeatBid().getBids());
        final Map<String, List<BidderBid>> impIdToBidderBids = bidderBids.stream()
                .collect(Collectors.groupingBy(bidderBid -> bidderBid.getBid().getImpid()));

        final Set<BidderBid> updatedBidderBids = impIdToBidderBids.values().stream()
                .map(BidResponseReducer::removeRedundantBidsForImp)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        if (bidderBids.size() == updatedBidderBids.size()) {
            return bidderResponse;
        }

        return updateBidderResponse(bidderResponse, updatedBidderBids);
    }

    private static List<BidderBid> removeRedundantBidsForImp(List<BidderBid> bidderBids) {
        return bidderBids.size() > 1 ? reduceBidsByImpId(bidderBids) : bidderBids;
    }

    private static List<BidderBid> reduceBidsByImpId(List<BidderBid> bidderBids) {
        return bidderBids.stream().anyMatch(bidderBid -> bidderBid.getBid().getDealid() != null)
                ? removeRedundantDealsBids(bidderBids)
                : removeRedundantForNonDealBids(bidderBids);
    }

    private static List<BidderBid> removeRedundantDealsBids(List<BidderBid> bidderBids) {
        final List<BidderBid> dealBidderBids = bidderBids.stream()
                .filter(bidderBid -> StringUtils.isNotBlank(bidderBid.getBid().getDealid()))
                .collect(Collectors.toList());

        return Collections.singletonList(getHighestPriceBid(bidderBids, dealBidderBids));
    }

    private static List<BidderBid> removeRedundantForNonDealBids(List<BidderBid> bidderBids) {
        return Collections.singletonList(getHighestPriceBid(bidderBids, bidderBids));
    }

    private static BidderBid getHighestPriceBid(List<BidderBid> bidderBids, List<BidderBid> dealBidderBids) {
        return dealBidderBids.stream()
                .max(Comparator.comparing(bidderBid -> bidderBid.getBid().getPrice(), Comparator.naturalOrder()))
                .orElse(bidderBids.get(0));
    }

    private static BidderResponse updateBidderResponse(BidderResponse bidderResponse,
                                                       Set<BidderBid> updatedBidderBids) {

        final BidderSeatBid seatBid = bidderResponse.getSeatBid();
        final BidderSeatBid updatedSeatBid = BidderSeatBid.of(
                new ArrayList<>(updatedBidderBids),
                seatBid.getHttpCalls(),
                seatBid.getErrors());

        return BidderResponse.of(bidderResponse.getBidder(), updatedSeatBid, bidderResponse.getResponseTime());
    }
}

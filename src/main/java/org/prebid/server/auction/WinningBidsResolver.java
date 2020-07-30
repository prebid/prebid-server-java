package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpTargeting;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class for removing bids from response for the same bidder-imp pair.
 */
public class WinningBidsResolver {

    private final JacksonMapper mapper;

    public WinningBidsResolver(JacksonMapper jacksonMapper) {
        this.mapper = Objects.requireNonNull(jacksonMapper);
    }

    /**
     * Removes {@link Bid}s with the same impId taking into account if {@link Bid} has deal.
     * <p>
     * Returns given list of {@link BidderResponse}s if {@link Bid}s have different impIds.
     */
    public BidderResponse resolveWinningBidsPerImpBidder(BidderResponse bidderResponse, List<Imp> imps,
                                                         boolean accountPreferDeals) {
        final Map<String, Boolean> impPreferDeals = extractDealsPreferenceByImp(imps, accountPreferDeals);
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();
        final List<BidderBid> responseBidderBids = ListUtils.emptyIfNull(seatBid.getBids());
        final Map<String, List<BidderBid>> impsIdToBidderBids = responseBidderBids.stream()
                .collect(Collectors.groupingBy(bidderBid -> bidderBid.getBid().getImpid()));

        final List<BidderBid> mostValuableBids = impsIdToBidderBids.entrySet().stream()
                .map(impToBidderBid -> mostValuableBid(impToBidderBid.getValue(),
                        impPreferDeals.get(impToBidderBid.getKey())))
                .collect(Collectors.toList());

        return responseBidderBids.size() == mostValuableBids.size()
                ? bidderResponse
                : BidderResponse.of(bidderResponse.getBidder(), BidderSeatBid.of(mostValuableBids,
                seatBid.getHttpCalls(), seatBid.getErrors()), bidderResponse.getResponseTime());
    }

    /**
     * Returns winning {@link Bid} among all bidders takes into account bid deal and account
     * or impression deals preferences.
     */
    public Set<Bid> resolveWinningBids(List<BidderResponse> bidderResponses, List<Imp> imps,
                                       boolean accountPreferDeals) {
        final Map<String, Boolean> impPreferDeals = extractDealsPreferenceByImp(imps, accountPreferDeals);
        final Map<String, Bid> winningBidsMap = new HashMap<>();
        for (BidderResponse bidderResponse : bidderResponses) {
            for (BidderBid bidderBid : bidderResponse.getSeatBid().getBids()) {
                final Bid bid = bidderBid.getBid();
                tryAddWinningBid(bid, winningBidsMap, impPreferDeals.get(bid.getImpid()));
            }
        }
        return new HashSet<>(winningBidsMap.values());
    }

    /**
     * Extract deals preference per impression.
     */
    private Map<String, Boolean> extractDealsPreferenceByImp(List<Imp> imps, boolean accountPreferDeals) {
        return imps.stream()
                .collect(Collectors.toMap(Imp::getId,
                        imp -> preferDeals(extractImpPreferDeal(imp), accountPreferDeals)));
    }

    /**
     * Returns most valuable {@link BidderBid} among all bids. Takes into account bid deals and preferDeal flag.
     */
    private static BidderBid mostValuableBid(List<BidderBid> bidderBids, boolean preferDeal) {
        if (bidderBids.size() == 1) {
            return bidderBids.get(0);
        }

        final List<BidderBid> processedBidderBids = preferDeal ? filterDealBids(bidderBids) : bidderBids;
        return processedBidderBids.stream()
                .max(Comparator.comparing(bidderBid -> bidderBid.getBid().getPrice(), Comparator.naturalOrder()))
                .orElse(bidderBids.get(0));
    }

    /**
     * Returns only {@link BidderBid} with deal id present.
     */
    private static List<BidderBid> filterDealBids(List<BidderBid> bidderBids) {
        List<BidderBid> processedBidderBids;
        final List<BidderBid> dealBidderBids = bidderBids.stream()
                .filter(bidderBid -> StringUtils.isNotBlank(bidderBid.getBid().getDealid()))
                .collect(Collectors.toList());

        processedBidderBids = dealBidderBids.isEmpty() ? bidderBids : dealBidderBids;
        return processedBidderBids;
    }

    /**
     * Tries to add a winning bid for each impId.
     */
    private static void tryAddWinningBid(Bid bid, Map<String, Bid> winningBids, boolean preferDeals) {
        final String impId = bid.getImpid();

        if (!winningBids.containsKey(impId) || isWinningBid(bid, winningBids.get(impId), preferDeals)) {
            winningBids.put(impId, bid);
        }
    }

    /**
     * Returns true if the first given {@link Bid} wins the previous winner, otherwise false.
     */
    private static boolean isWinningBid(Bid bid, Bid otherBid, boolean preferDeals) {
        return preferDeals ? isWinningHighDealPriority(bid, otherBid) : isWinnerBidByPrice(bid, otherBid);
    }

    private static boolean isWinningHighDealPriority(Bid bid, Bid otherBid) {
        final String bidDealId = bid.getDealid();
        final String otherBidDealId = otherBid.getDealid();
        if (bidDealId != null) {
            return otherBidDealId == null || isWinnerBidByPrice(bid, otherBid);
        } else {
            return otherBidDealId == null && isWinnerBidByPrice(bid, otherBid);
        }
    }

    /**
     * Returns true if the first given {@link Bid} has higher CPM than another one, otherwise false.
     */
    private static boolean isWinnerBidByPrice(Bid bid, Bid otherBid) {
        return bid.getPrice().compareTo(otherBid.getPrice()) > 0;
    }

    private static boolean preferDeals(Boolean impPreferDeals, boolean accountPreferDeals) {
        return impPreferDeals != null ? impPreferDeals : accountPreferDeals;
    }

    /**
     * Extracts prefer deals from {@link Imp}
     */
    private Boolean extractImpPreferDeal(Imp imp) {
        final ExtImp extImp;
        try {
            extImp = mapper.mapper().treeToValue(imp.getExt(), ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format(
                    "Incorrect Imp extension format for Imp with id %s: %s", imp.getId(), e.getMessage()));
        }
        final ExtImpTargeting extImpTargeting = extImp.getTargeting();
        return extImpTargeting != null ? extImpTargeting.getPreferDeals() : null;
    }
}

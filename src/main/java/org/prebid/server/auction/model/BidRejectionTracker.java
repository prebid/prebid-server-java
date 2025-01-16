package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.MapUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class BidRejectionTracker {

    private static final Logger logger = LoggerFactory.getLogger(BidRejectionTracker.class);

    private static final ConditionalLogger BID_REJECTIONS_LOGGER =
            new ConditionalLogger("multiple-bid-rejections", logger);

    private static final String MULTIPLE_REJECTIONS_WARNING_TEMPLATE =
            "Warning: bidder %s on imp %s responded with multiple nonbid reasons.";

    private static final String INCONSISTENT_RESPONSES_WARNING_TEMPLATE =
            "Warning: Inconsistent responses from bidder %s on imp %s: both bids and nonbids.";

    private final double logSamplingRate;
    private final String bidder;
    private final Set<String> involvedImpIds;
    private final Map<String, Set<String>> succeededBidsIds;
    private final Map<String, List<Pair<BidderBid, BidRejectionReason>>> rejectedBids;

    public BidRejectionTracker(String bidder, Set<String> involvedImpIds, double logSamplingRate) {
        this.bidder = bidder;
        this.involvedImpIds = new HashSet<>(involvedImpIds);
        this.logSamplingRate = logSamplingRate;

        succeededBidsIds = new HashMap<>();
        rejectedBids = new HashMap<>();
    }

    /**
     * Restores ONLY imps from rejection, rejected bids are preserved for analytics.
     * A bid can be rejected only once.
     */
    public void succeed(Collection<BidderBid> bids) {
        bids.stream()
                .map(BidderBid::getBid)
                .filter(Objects::nonNull)
                .forEach(this::succeed);
    }

    private void succeed(Bid bid) {
        final String bidId = bid.getId();
        final String impId = bid.getImpid();
        if (involvedImpIds.contains(impId)) {
            succeededBidsIds.computeIfAbsent(impId, key -> new HashSet<>()).add(bidId);
            if (rejectedBids.containsKey(impId)) {
                BID_REJECTIONS_LOGGER.warn(
                        INCONSISTENT_RESPONSES_WARNING_TEMPLATE.formatted(bidder, impId),
                        logSamplingRate);
            }
        }
    }

    public void restoreFromRejection(Collection<BidderBid> bids) {
        succeed(bids);
    }

    public void rejectBids(Collection<BidderBid> bidderBids, BidRejectionReason reason) {
        bidderBids.forEach(bidderBid -> rejectBid(bidderBid, reason));
    }

    public void rejectBid(BidderBid bidderBid, BidRejectionReason reason) {
        final Bid bid = bidderBid.getBid();
        final String impId = bid.getImpid();

        reject(impId, bidderBid, reason);
    }

    private void reject(String impId, BidderBid bid, BidRejectionReason reason) {
        if (involvedImpIds.contains(impId)) {
            if (rejectedBids.containsKey(impId)) {
                BID_REJECTIONS_LOGGER.warn(
                        MULTIPLE_REJECTIONS_WARNING_TEMPLATE.formatted(bidder, impId), logSamplingRate);
            }

            rejectedBids.computeIfAbsent(impId, key -> new ArrayList<>()).add(Pair.of(bid, reason));

            if (succeededBidsIds.containsKey(impId)) {
                final String bidId = Optional.ofNullable(bid).map(BidderBid::getBid).map(Bid::getId).orElse(null);
                final Set<String> succeededBids = succeededBidsIds.get(impId);
                final boolean removed = bidId == null || succeededBids.remove(bidId);
                if (removed && !succeededBids.isEmpty()) {
                    BID_REJECTIONS_LOGGER.warn(
                            INCONSISTENT_RESPONSES_WARNING_TEMPLATE.formatted(bidder, impId),
                            logSamplingRate);
                }
            }
        }
    }

    public void rejectImps(Collection<String> impIds, BidRejectionReason reason) {
        impIds.forEach(impId -> rejectImp(impId, reason));
    }

    public void rejectImp(String impId, BidRejectionReason reason) {
        if (reason.getValue() >= 300) {
            throw new IllegalArgumentException("The non-bid code 300 and higher assumes "
                    + "that there is a rejected bid that shouldn't be lost");
        }
        reject(impId, null, reason);
    }

    public void rejectAllImps(BidRejectionReason reason) {
        involvedImpIds.forEach(impId -> rejectImp(impId, reason));
    }

    /**
     * If an impression has at least one valid bid, it's not considered rejected.
     * If no valid bids are returned for the impression, only the first one rejected reason will be returned
     */
    public Map<String, BidRejectionReason> getRejectedImps() {
        final Map<String, BidRejectionReason> rejectedImpIds = new HashMap<>();
        for (String impId : involvedImpIds) {
            final Set<String> succeededBids = succeededBidsIds.getOrDefault(impId, Collections.emptySet());
            if (succeededBids.isEmpty()) {
                if (rejectedBids.containsKey(impId)) {
                    rejectedImpIds.put(impId, rejectedBids.get(impId).getFirst().getRight());
                } else {
                    rejectedImpIds.put(impId, BidRejectionReason.NO_BID);
                }
            }
        }

        return rejectedImpIds;
    }

    /**
     * Bid is absent for the non-bid code from 0 to 299
     */
    public Map<String, List<Pair<BidderBid, BidRejectionReason>>> getRejectedBids() {
        final Map<String, List<Pair<BidderBid, BidRejectionReason>>> missingImpIds = new HashMap<>();
        for (String impId : involvedImpIds) {
            final Set<String> succeededBids = succeededBidsIds.getOrDefault(impId, Collections.emptySet());
            if (succeededBids.isEmpty() && !rejectedBids.containsKey(impId)) {
                missingImpIds.computeIfAbsent(impId, key -> new ArrayList<>())
                        .add(Pair.of(null, BidRejectionReason.NO_BID));
            }
        }

        return MapUtil.merge(missingImpIds, rejectedBids);
    }
}

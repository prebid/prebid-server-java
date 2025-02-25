package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
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
    private final Map<String, List<Rejected>> rejectedBids;

    public BidRejectionTracker(String bidder, Set<String> involvedImpIds, double logSamplingRate) {
        this.bidder = bidder;
        this.involvedImpIds = new HashSet<>(involvedImpIds);
        this.logSamplingRate = logSamplingRate;

        succeededBidsIds = new HashMap<>();
        rejectedBids = new HashMap<>();
    }

    public BidRejectionTracker(BidRejectionTracker anotherTracker, Set<String> additionalImpIds) {
        this.bidder = anotherTracker.bidder;
        this.logSamplingRate = anotherTracker.logSamplingRate;
        this.involvedImpIds = new HashSet<>(anotherTracker.involvedImpIds);
        this.involvedImpIds.addAll(additionalImpIds);

        this.succeededBidsIds = new HashMap<>(anotherTracker.succeededBidsIds);
        this.rejectedBids = new HashMap<>(anotherTracker.rejectedBids);
    }

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

    public void reject(Collection<Rejected> rejections) {
        rejections.forEach(this::reject);
    }

    public void reject(Rejected rejected) {
        if (rejected instanceof RejectedImp && rejected.reason().getValue() >= 300) {
            logger.warn("The rejected imp {} with the code {} equal to or higher than 300 assumes "
                    + "that there is a rejected bid that shouldn't be lost");
        }

        final String impId = rejected.impId();
        if (involvedImpIds.contains(impId)) {
            if (rejectedBids.containsKey(impId)) {
                BID_REJECTIONS_LOGGER.warn(
                        MULTIPLE_REJECTIONS_WARNING_TEMPLATE.formatted(bidder, impId), logSamplingRate);
            }

            rejectedBids.computeIfAbsent(impId, key -> new ArrayList<>()).add(rejected);

            if (succeededBidsIds.containsKey(impId)) {
                final String bidId = rejected instanceof RejectedBid ? ((RejectedBid) rejected).bidId() : null;
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
        impIds.forEach(impId -> reject(RejectedImp.of(impId, reason)));
    }

    public void rejectAll(BidRejectionReason reason) {
        involvedImpIds.forEach(impId -> reject(RejectedImp.of(impId, reason)));
    }

    public Set<RejectedImp> getRejectedImps() {
        final Set<RejectedImp> rejectedImpIds = new HashSet<>();
        for (String impId : involvedImpIds) {
            final Set<String> succeededBids = succeededBidsIds.getOrDefault(impId, Collections.emptySet());
            if (succeededBids.isEmpty()) {
                if (rejectedBids.containsKey(impId)) {
                    rejectedImpIds.add(RejectedImp.of(impId, rejectedBids.get(impId).getFirst().reason()));
                } else {
                    rejectedImpIds.add(RejectedImp.of(impId, BidRejectionReason.NO_BID));
                }
            }
        }

        return rejectedImpIds;
    }

    public Map<String, List<Rejected>> getAllRejected() {
        final Map<String, List<Rejected>> missingImpIds = new HashMap<>();
        for (String impId : involvedImpIds) {
            final Set<String> succeededBids = succeededBidsIds.getOrDefault(impId, Collections.emptySet());
            if (succeededBids.isEmpty() && !rejectedBids.containsKey(impId)) {
                missingImpIds.computeIfAbsent(impId, key -> new ArrayList<>())
                        .add(RejectedImp.of(impId, BidRejectionReason.NO_BID));
            }
        }

        return MapUtil.merge(missingImpIds, rejectedBids);
    }
}

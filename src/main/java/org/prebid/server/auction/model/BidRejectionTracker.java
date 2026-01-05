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

    private static final ConditionalLogger bidRejectionsLogger =
            new ConditionalLogger("multiple-bid-rejections", logger);

    private static final String MULTIPLE_REJECTIONS_WARNING_TEMPLATE =
            "Warning: bidder %s on imp %s responded with multiple nonbid reasons.";

    private static final String INCONSISTENT_RESPONSES_WARNING_TEMPLATE =
            "Warning: Inconsistent responses from bidder %s on imp %s: both bids and nonbids.";

    private final double logSamplingRate;
    private final String bidder;
    private final Set<String> involvedImpIds;
    private final Map<String, Set<String>> succeededBidsIds;
    private final Map<String, List<Rejection>> rejections;

    public BidRejectionTracker(String bidder, Set<String> involvedImpIds, double logSamplingRate) {
        this.bidder = bidder;
        this.involvedImpIds = new HashSet<>(involvedImpIds);
        this.logSamplingRate = logSamplingRate;

        succeededBidsIds = new HashMap<>();
        rejections = new HashMap<>();
    }

    private BidRejectionTracker(
            String bidder,
            Set<String> involvedImpIds,
            Map<String, Set<String>> succeededBidsIds,
            Map<String, List<Rejection>> rejections,
            double logSamplingRate
    ) {
        this.bidder = bidder;
        this.involvedImpIds = new HashSet<>(involvedImpIds);
        this.logSamplingRate = logSamplingRate;

        this.succeededBidsIds = MapUtil.mapValues(succeededBidsIds, v -> new HashSet<>(v));
        this.rejections = MapUtil.mapValues(rejections, ArrayList::new);
    }

    public static BidRejectionTracker copyOf(BidRejectionTracker anotherTracker) {
        return new BidRejectionTracker(
                anotherTracker.bidder,
                anotherTracker.involvedImpIds,
                anotherTracker.succeededBidsIds,
                anotherTracker.rejections,
                anotherTracker.logSamplingRate
        );
    }

    public static BidRejectionTracker withAdditionalImpIds(
            BidRejectionTracker anotherTracker,
            Set<String> additionalImpIds
    ) {
        final BidRejectionTracker newTracker = copyOf(anotherTracker);
        newTracker.involvedImpIds.addAll(additionalImpIds);
        return newTracker;
    }

    public BidRejectionTracker succeed(Collection<BidderBid> bids) {
        bids.stream()
                .map(BidderBid::getBid)
                .filter(Objects::nonNull)
                .forEach(this::succeed);
        return this;
    }

    private BidRejectionTracker succeed(Bid bid) {
        final String bidId = bid.getId();
        final String impId = bid.getImpid();
        if (involvedImpIds.contains(impId)) {
            succeededBidsIds.computeIfAbsent(impId, key -> new HashSet<>()).add(bidId);
            if (rejections.containsKey(impId)) {
                bidRejectionsLogger.warn(
                        INCONSISTENT_RESPONSES_WARNING_TEMPLATE.formatted(bidder, impId),
                        logSamplingRate);
            }
        }
        return this;
    }

    public void restoreFromRejection(Collection<BidderBid> bids) {
        succeed(bids);
    }

    public BidRejectionTracker reject(Collection<Rejection> rejections) {
        rejections.forEach(this::reject);
        return this;
    }

    public BidRejectionTracker reject(Rejection rejection) {
        if (rejection instanceof ImpRejection && rejection.reason().getValue() >= 300) {
            logger.warn("The rejected imp {} with the code {} equal to or higher than 300 assumes "
                    + "that there is a rejected bid that shouldn't be lost");
            return this;
        }

        final String impId = rejection.impId();
        if (involvedImpIds.contains(impId)) {
            if (rejections.containsKey(impId)) {
                bidRejectionsLogger.warn(
                        MULTIPLE_REJECTIONS_WARNING_TEMPLATE.formatted(bidder, impId), logSamplingRate);
            }

            rejections.computeIfAbsent(impId, key -> new ArrayList<>())
                    .add(rejection instanceof ImpRejection
                            ? ImpRejection.of(bidder, rejection.impId(), rejection.reason())
                            : rejection);

            if (succeededBidsIds.containsKey(impId)) {
                final String bidId = rejection instanceof BidRejection ? ((BidRejection) rejection).bidId() : null;
                final Set<String> succeededBids = succeededBidsIds.get(impId);
                final boolean removed = bidId == null || succeededBids.remove(bidId);
                if (removed && !succeededBids.isEmpty()) {
                    bidRejectionsLogger.warn(
                            INCONSISTENT_RESPONSES_WARNING_TEMPLATE.formatted(bidder, impId),
                            logSamplingRate);
                }
            }
        }

        return this;
    }

    public BidRejectionTracker rejectImps(Collection<String> impIds, BidRejectionReason reason) {
        impIds.forEach(impId -> reject(ImpRejection.of(impId, reason)));
        return this;
    }

    public BidRejectionTracker rejectAll(BidRejectionReason reason) {
        involvedImpIds.forEach(impId -> reject(ImpRejection.of(impId, reason)));
        return this;
    }

    public Set<Rejection> getRejected() {
        final Set<Rejection> rejectedResult = new HashSet<>();
        for (String impId : involvedImpIds) {
            final Set<String> succeededBids = succeededBidsIds.getOrDefault(impId, Collections.emptySet());
            if (succeededBids.isEmpty()) {
                if (rejections.containsKey(impId)) {
                    rejectedResult.add(rejections.get(impId).getFirst());
                } else {
                    rejectedResult.add(ImpRejection.of(bidder, impId, BidRejectionReason.NO_BID));
                }
            }
        }

        return rejectedResult;
    }

    public Map<String, List<Rejection>> getAllRejected() {
        final Map<String, List<Rejection>> missingImpIds = new HashMap<>();
        for (String impId : involvedImpIds) {
            final Set<String> succeededBids = succeededBidsIds.getOrDefault(impId, Collections.emptySet());
            if (succeededBids.isEmpty() && !rejections.containsKey(impId)) {
                missingImpIds.computeIfAbsent(impId, key -> new ArrayList<>())
                        .add(ImpRejection.of(bidder, impId, BidRejectionReason.NO_BID));
            }
        }

        return MapUtil.merge(missingImpIds, rejections);
    }
}

package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.MapUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BidRejectionTracker {

    private static final Logger logger = LoggerFactory.getLogger(BidRejectionTracker.class);

    private static final ConditionalLogger MULTIPLE_BID_REJECTIONS_LOGGER =
            new ConditionalLogger("multiple-bid-rejections", logger);

    private static final String WARNING_TEMPLATE =
            "Bid with imp id: %s for bidder: %s rejected due to: %s, but has been already rejected";

    private final double logSamplingRate;
    private final String bidder;
    private final Set<String> involvedImpIds;
    private final Set<String> succeededImpIds;
    private final Map<String, BidRejectionReason> rejectedImpIds;

    public BidRejectionTracker(String bidder, Set<String> involvedImpIds, double logSamplingRate) {
        this.bidder = bidder;
        this.involvedImpIds = new HashSet<>(involvedImpIds);
        this.logSamplingRate = logSamplingRate;

        succeededImpIds = new HashSet<>();
        rejectedImpIds = new HashMap<>();
    }

    public void succeed(String impId) {
        if (involvedImpIds.contains(impId)) {
            succeededImpIds.add(impId);
            rejectedImpIds.remove(impId);
        }
    }

    public void succeed(Collection<BidderBid> bids) {
        bids.stream()
                .map(BidderBid::getBid)
                .filter(Objects::nonNull)
                .map(Bid::getImpid)
                .filter(Objects::nonNull)
                .forEach(this::succeed);
    }

    public void restoreFromRejection(Collection<BidderBid> bids) {
        succeed(bids);
    }

    public void reject(String impId, BidRejectionReason reason) {
        if (involvedImpIds.contains(impId) && !rejectedImpIds.containsKey(impId)) {
            rejectedImpIds.put(impId, reason);
            succeededImpIds.remove(impId);
        } else if (rejectedImpIds.containsKey(impId)) {
            MULTIPLE_BID_REJECTIONS_LOGGER.warn(
                    WARNING_TEMPLATE.formatted(impId, bidder, reason), logSamplingRate);
        }
    }

    public void reject(Collection<String> impIds, BidRejectionReason reason) {
        impIds.forEach(impId -> reject(impId, reason));
    }

    public void rejectAll(BidRejectionReason reason) {
        involvedImpIds.forEach(impId -> reject(impId, reason));
    }

    public Map<String, BidRejectionReason> getRejectionReasons() {
        final Map<String, BidRejectionReason> missingImpIds = new HashMap<>();
        for (String impId : involvedImpIds) {
            if (!succeededImpIds.contains(impId) && !rejectedImpIds.containsKey(impId)) {
                missingImpIds.put(impId, BidRejectionReason.NO_BID);
            }
        }

        return MapUtil.merge(missingImpIds, rejectedImpIds);
    }
}

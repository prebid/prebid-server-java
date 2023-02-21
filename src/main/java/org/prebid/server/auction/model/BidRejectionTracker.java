package org.prebid.server.auction.model;

import org.prebid.server.util.MapUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BidRejectionTracker {

    private final Set<String> involvedImpIds;
    private final Set<String> succeededImpIds;
    private final Map<String, BidRejectionReason> rejectedImpIds;

    public BidRejectionTracker(Set<String> involvedImpIds) {
        this.involvedImpIds = new HashSet<>(involvedImpIds);
        succeededImpIds = new HashSet<>();
        rejectedImpIds = new HashMap<>();
    }

    public BidRejectionTracker succeed(String impId) {
        succeededImpIds.add(impId);
        rejectedImpIds.remove(impId);
        return this;
    }

    public BidRejectionTracker reject(String impId, BidRejectionReason reason) {
        rejectedImpIds.put(impId, reason);
        succeededImpIds.remove(impId);
        return this;
    }

    public BidRejectionTracker reject(Collection<String> impIds, BidRejectionReason reason) {
        impIds.forEach(impId -> rejectedImpIds.put(impId, reason));
        return this;
    }

    public BidRejectionTracker rejectAll(BidRejectionReason reason) {
        final Map<String, BidRejectionReason> rejectionReasons = involvedImpIds.stream()
                .map(impId -> Map.entry(impId, reason))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        rejectedImpIds.putAll(rejectionReasons);
        return this;
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

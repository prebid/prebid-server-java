package org.prebid.server.cookie.model;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.bidder.UsersyncMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Accessors(fluent = true)
@Builder(toBuilder = true)
@Value(staticConstructor = "of")
public class BiddersContext {

    @Builder.Default
    Set<String> requestedBidders = new HashSet<>();

    @Builder.Default
    Set<String> coopSyncBidders = new HashSet<>();

    @Builder.Default
    Set<String> multiSyncBidders = new HashSet<>();

    @Builder.Default
    Map<String, RejectionReason> rejectedBidders = new HashMap<>();

    @Builder.Default
    Map<String, UsersyncMethod> bidderUsersyncMethod = new HashMap<>();

    public boolean isRequested(String bidder) {
        return requestedBidders.contains(bidder);
    }

    public boolean isCoopSync(String bidder) {
        return coopSyncBidders.contains(bidder);
    }

    private Set<String> involvedBidders() {
        return SetUtils.union(
                multiSyncBidders,
                SetUtils.union(requestedBidders, coopSyncBidders));
    }

    public Set<String> allowedBidders() {
        return SetUtils.difference(involvedBidders(), rejectedBidders.keySet());
    }

    public Set<String> allowedRequestedBidders() {
        return SetUtils.difference(requestedBidders, rejectedBidders.keySet());
    }

    public Set<String> allowedCoopSyncBidders() {
        return SetUtils.difference(coopSyncBidders, rejectedBidders.keySet());
    }

    public Set<String> allowedMultisyncBidders() {
        return SetUtils.difference(multiSyncBidders, rejectedBidders.keySet());
    }

    public BiddersContext withRejectedBidder(String bidder, RejectionReason reason) {
        return withRejectedBidders(Collections.singleton(bidder), reason);
    }

    public BiddersContext withRejectedBidders(Collection<String> bidders, RejectionReason reason) {
        if (bidders.isEmpty()) {
            return this;
        }

        final Map<String, RejectionReason> updatedRejectedBidders = new HashMap<>(rejectedBidders);
        final Map<String, UsersyncMethod> updatedMethods = new HashMap<>(bidderUsersyncMethod);

        for (String bidder : bidders) {
            updatedRejectedBidders.put(bidder, reason);
            updatedMethods.remove(bidder);
        }

        return toBuilder()
                .rejectedBidders(updatedRejectedBidders)
                .bidderUsersyncMethod(updatedMethods)
                .build();
    }

    public BiddersContext withBidderUsersyncMethod(String bidder, UsersyncMethod method) {
        if (rejectedBidders.containsKey(bidder)) {
            return this;
        }

        final Map<String, UsersyncMethod> updatedMethods = new HashMap<>(bidderUsersyncMethod);
        updatedMethods.put(bidder, method);

        return toBuilder().bidderUsersyncMethod(updatedMethods).build();
    }
}

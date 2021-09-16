package org.prebid.server.deals.model;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(staticName = "create")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@EqualsAndHashCode
public class TxnLog {

    Set<String> lineItemsMatchedDomainTargeting = new HashSet<>();

    Set<String> lineItemsMatchedWholeTargeting = new HashSet<>();

    Set<String> lineItemsMatchedTargetingFcapped = new HashSet<>();

    Set<String> lineItemsMatchedTargetingFcapLookupFailed = new HashSet<>();

    Set<String> lineItemsReadyToServe = new HashSet<>();

    Set<String> lineItemsPacingDeferred = new HashSet<>();

    Map<String, Set<String>> lineItemsSentToBidder = MapUtils.lazyMap(new HashMap<>(),
            (Factory<Set<String>>) HashSet::new);

    Map<String, Set<String>> lineItemsSentToBidderAsTopMatch = MapUtils.lazyMap(new HashMap<>(),
            (Factory<Set<String>>) HashSet::new);

    Map<String, Set<String>> lineItemsReceivedFromBidder = MapUtils.lazyMap(new HashMap<>(),
            (Factory<Set<String>>) HashSet::new);

    Set<String> lineItemsResponseInvalidated = new HashSet<>();

    Set<String> lineItemsSentToClient = new HashSet<>();

    Map<String, Set<String>> lostMatchingToLineItems = MapUtils.lazyMap(new HashMap<>(),
            (Factory<Set<String>>) HashSet::new);

    Map<String, Set<String>> lostAuctionToLineItems = MapUtils.lazyMap(new HashMap<>(),
            (Factory<Set<String>>) HashSet::new);

    Set<String> lineItemSentToClientAsTopMatch = new HashSet<>();
}

package org.prebid.server.cookie;

import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PrioritizedCoopSyncProvider {

    private static final Logger logger = LoggerFactory.getLogger(PrioritizedCoopSyncProvider.class);

    private final Set<String> prioritizedBidders;
    private final Set<String> prioritizedCookieFamilyNames;

    public PrioritizedCoopSyncProvider(Set<String> bidders, BidderCatalog bidderCatalog) {
        this.prioritizedBidders = validCoopSyncBidders(Objects.requireNonNull(bidders), bidderCatalog);
        this.prioritizedCookieFamilyNames = prioritizedBidders.stream()
                .map(bidder -> bidderCatalog.cookieFamilyName(bidder).orElseThrow())
                .collect(Collectors.toSet());
    }

    private static Set<String> validCoopSyncBidders(Set<String> bidders, BidderCatalog bidderCatalog) {
        final Set<String> validBidders = new HashSet<>();

        for (String bidder : bidders) {
            if (!bidderCatalog.isValidName(bidder)) {
                logger.info("""
                        bidder {} is provided for prioritized coop-syncing, \
                        but is invalid bidder name, ignoring""", bidder);
            } else if (!bidderCatalog.isActive(bidder)) {
                logger.info("""
                        bidder {} is provided for prioritized coop-syncing, \
                        but disabled in current pbs instance, ignoring""", bidder);
            } else if (bidderCatalog.usersyncerByName(bidder).isEmpty()) {
                logger.info("""
                        bidder {} is provided for prioritized coop-syncing, \
                        but has no user-sync configuration, ignoring""", bidder);
            } else {
                validBidders.add(bidder);
            }
        }

        return validBidders;
    }

    public Set<String> prioritizedBidders(Account account) {
        final Set<String> resolvedBidders = Optional.ofNullable(account)
                .map(Account::getCookieSync)
                .map(AccountCookieSyncConfig::getPrioritizedBidders)
                .filter(bidders -> !bidders.isEmpty())
                .orElse(prioritizedBidders);

        final List<String> shuffledBidders = new ArrayList<>(resolvedBidders);
        Collections.shuffle(shuffledBidders);

        return new LinkedHashSet<>(shuffledBidders);
    }

    public boolean isPrioritizedFamily(String cookieFamilyName) {
        return prioritizedCookieFamilyNames.contains(cookieFamilyName);
    }
}

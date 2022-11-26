package org.prebid.server.cookie;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountCoopSyncConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CoopSyncProvider {

    private static final Logger logger = LoggerFactory.getLogger(CoopSyncProvider.class);

    private final Set<String> coopSyncBidders;
    private final Set<String> prioritizedBidders;

    private final boolean defaultCoopSync;

    public CoopSyncProvider(BidderCatalog bidderCatalog, Set<String> prioritizedBidders, boolean defaultCoopSync) {
        this.coopSyncBidders = Objects.requireNonNull(bidderCatalog).usersyncReadyBidders();
        this.prioritizedBidders = validCoopSyncBidders(SetUtils.emptyIfNull(prioritizedBidders), bidderCatalog);

        this.defaultCoopSync = defaultCoopSync;
    }

    private static Set<String> validCoopSyncBidders(Set<String> bidders, BidderCatalog bidderCatalog) {
        final Set<String> validBidders = new HashSet<>();

        for (String bidder : bidders) {
            if (!bidderCatalog.isValidName(bidder)) {
                logger.info("""
                        bidder {0} is provided for prioritized coop-syncing, \
                        but is invalid bidder name, ignoring""", bidder);
            } else if (!bidderCatalog.isActive(bidder)) {
                logger.info("""
                        bidder {0} is provided for prioritized coop-syncing, \
                        but disabled in current pbs instance, ignoring""", bidder);
            } else if (bidderCatalog.usersyncerByName(bidder).isEmpty()) {
                logger.info("""
                        bidder {0} is provided for prioritized coop-syncing, \
                        but has no user-sync configuration, ignoring""", bidder);
            } else {
                validBidders.add(bidder);
            }
        }

        return validBidders;
    }

    public Set<String> coopSyncBidders(CookieSyncContext cookieSyncContext) {
        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
        final Account account = cookieSyncContext.getAccount();

        return coopSyncAllowed(cookieSyncRequest, account)
                ? prepareCoopSyncBidders()
                : Collections.emptySet();
    }

    private boolean coopSyncAllowed(CookieSyncRequest cookieSyncRequest, Account account) {
        return Optional.ofNullable(cookieSyncRequest.getCoopSync())
                .or(() -> Optional.ofNullable(account)
                        .map(Account::getCookieSync)
                        .map(AccountCookieSyncConfig::getCoopSync)
                        .map(AccountCoopSyncConfig::getEnabled))
                .orElse(defaultCoopSync);
    }

    private Set<String> prepareCoopSyncBidders() {
        final List<String> shuffledPrioritizedBidders = new ArrayList<>(prioritizedBidders);
        Collections.shuffle(shuffledPrioritizedBidders);

        return Stream.concat(shuffledPrioritizedBidders.stream(), coopSyncBidders.stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)); // to keep prioritized bidders first
    }
}

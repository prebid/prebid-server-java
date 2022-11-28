package org.prebid.server.cookie;

import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountCoopSyncConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CoopSyncProvider {

    private final Set<String> coopSyncBidders;
    private final PrioritizedCoopSyncProvider prioritizedCoopSyncProvider;

    private final boolean defaultCoopSync;

    public CoopSyncProvider(BidderCatalog bidderCatalog,
                            PrioritizedCoopSyncProvider prioritizedCoopSyncProvider,
                            boolean defaultCoopSync) {

        this.coopSyncBidders = Objects.requireNonNull(bidderCatalog).usersyncReadyBidders();
        this.prioritizedCoopSyncProvider = Objects.requireNonNull(prioritizedCoopSyncProvider);

        this.defaultCoopSync = defaultCoopSync;
    }

    public Set<String> coopSyncBidders(CookieSyncContext cookieSyncContext) {
        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
        final Account account = cookieSyncContext.getAccount();

        return coopSyncAllowed(cookieSyncRequest, account)
                ? prepareCoopSyncBidders(account)
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

    private Set<String> prepareCoopSyncBidders(Account account) {
        final List<String> shuffledCoopSyncBidders = new ArrayList<>(coopSyncBidders);
        Collections.shuffle(shuffledCoopSyncBidders);

        return Stream.of(prioritizedCoopSyncProvider.prioritizedBidders(account), shuffledCoopSyncBidders)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new)); // to keep prioritized bidders first
    }
}

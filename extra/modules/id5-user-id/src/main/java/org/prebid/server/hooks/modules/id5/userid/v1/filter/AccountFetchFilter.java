package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.prebid.server.hooks.modules.id5.userid.v1.config.ValuesFilter;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.settings.model.Account;

import java.util.Objects;

/**
 * Filters fetch invocation by account id using {@link ValuesFilter} configuration.
 */
public class AccountFetchFilter implements FetchActionFilter {

    private final ValuesFilter<String> accountFilter;

    public AccountFetchFilter(ValuesFilter<String> accountFilter) {
        this.accountFilter = Objects.requireNonNull(accountFilter);
    }

    @Override
    public FilterResult shouldInvoke(AuctionRequestPayload payload, AuctionInvocationContext invocationContext) {
        final Account account = invocationContext.auctionContext().getAccount();
        final String accountId = account != null ? account.getId() : null;
        if (accountId == null || accountId.isBlank()) {
            return FilterResult.rejected("missing account id");
        }
        return accountFilter.isValueAllowed(accountId)
                ? FilterResult.accepted()
                : FilterResult.rejected("account " + accountId + " rejected by config");
    }
}

package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.AccountControl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.AccountFilter;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.settings.model.Account;

import java.util.List;

public final class AccountControlImp implements AccountControl {
    private final AccountFilter filter;

    public AccountControlImp(AccountFilter filter) {
        this.filter = filter;
    }


    @Override
    public boolean test(AuctionInvocationContext invocationContext) {
        if (filter == null) {
            return true;
        }
        final List<String> allowList = filter.getAllowList();
        final boolean hasAllowList = (allowList != null && !allowList.isEmpty());
        do {
            if (invocationContext == null) {
                break;
            }
            final AuctionContext auctionContext = invocationContext.auctionContext();
            if (auctionContext == null) {
                break;
            }
            final Account account = auctionContext.getAccount();
            if (account == null) {
                break;
            }
            final String accountId = account.getId();
            if (accountId == null || accountId.isEmpty()) {
                break;
            }
            if (hasAllowList) {
                return allowList.contains(accountId);
            }
        } while(false);
        return !hasAllowList;
    }
}

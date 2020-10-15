package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Objects;
import java.util.Set;

public class EnrichingApplicationSettings implements ApplicationSettings {

    private final ApplicationSettings delegate;
    private final Account defaultAccount;

    public EnrichingApplicationSettings(ApplicationSettings delegate, Account defaultAccount) {
        this.delegate = Objects.requireNonNull(delegate);
        this.defaultAccount = Objects.equals(Account.builder().build(), defaultAccount) ? null : defaultAccount;
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        final Future<Account> accountFuture = delegate.getAccountById(accountId, timeout);

        if (defaultAccount == null) {
            return accountFuture;
        }

        return accountFuture
                .map(account -> account.merge(defaultAccount))
                .otherwise(Account.empty(accountId).merge(defaultAccount));
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return delegate.getAdUnitConfigById(adUnitConfigId, timeout);
    }

    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return delegate.getStoredData(requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return delegate.getStoredResponses(responseIds, timeout);
    }

    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return delegate.getAmpStoredData(requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return delegate.getVideoStoredData(requestIds, impIds, timeout);
    }
}

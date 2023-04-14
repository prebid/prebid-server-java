package org.prebid.server.settings;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EnrichingApplicationSettings implements ApplicationSettings {

    private final boolean enforceValidAccount;
    private final ApplicationSettings delegate;
    private final PriceFloorsConfigResolver priceFloorsConfigResolver;
    private final JsonMerger jsonMerger;

    private final Account defaultAccount;

    public EnrichingApplicationSettings(boolean enforceValidAccount,
                                        String defaultAccountConfig,
                                        ApplicationSettings delegate,
                                        PriceFloorsConfigResolver priceFloorsConfigResolver,
                                        JsonMerger jsonMerger,
                                        JacksonMapper mapper) {

        this.enforceValidAccount = enforceValidAccount;
        this.delegate = Objects.requireNonNull(delegate);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.priceFloorsConfigResolver = Objects.requireNonNull(priceFloorsConfigResolver);

        defaultAccount = parseAccount(defaultAccountConfig, mapper);
    }

    private static Account parseAccount(String accountConfig, JacksonMapper mapper) {
        try {
            final Account account = StringUtils.isNotBlank(accountConfig)
                    ? mapper.decodeValue(accountConfig, Account.class)
                    : null;

            return isNotEmpty(account) ? account : null;
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Could not parse default account configuration", e);
        }
    }

    private static boolean isNotEmpty(Account account) {
        return account != null && !account.equals(Account.builder().build());
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return delegate.getAccountById(accountId, timeout)
                .compose(priceFloorsConfigResolver::updateFloorsConfig)
                .map(this::mergeAccounts)
                .recover(throwable -> recoverIfNeeded(throwable, accountId));
    }

    @Override
    public Future<StoredDataResult> getStoredData(String accountId,
                                                  Set<String> requestIds,
                                                  Set<String> impIds,
                                                  Timeout timeout) {

        return delegate.getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return delegate.getStoredResponses(responseIds, timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return delegate.getCategories(primaryAdServer, publisher, timeout);
    }

    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
                                                     Timeout timeout) {

        return delegate.getAmpStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId,
                                                       Set<String> requestIds,
                                                       Set<String> impIds,
                                                       Timeout timeout) {

        return delegate.getVideoStoredData(accountId, requestIds, impIds, timeout);
    }

    private Account mergeAccounts(Account account) {
        return defaultAccount != null
                ? jsonMerger.merge(account, defaultAccount, Account.class)
                : account;
    }

    private Future<Account> recoverIfNeeded(Throwable throwable, String accountId) {
        // In case of invalid account return failed future
        return !enforceValidAccount
                ? Future.succeededFuture(mergeAccounts(Account.empty(accountId)))
                : Future.failedFuture(throwable);
    }
}

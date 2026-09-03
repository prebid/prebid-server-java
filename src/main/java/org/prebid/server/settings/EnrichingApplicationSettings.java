package org.prebid.server.settings;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.ActivitiesConfigResolver;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class EnrichingApplicationSettings implements ApplicationSettings {

    private final boolean enforceValidAccount;
    private final ApplicationSettings delegate;
    private final PriceFloorsConfigResolver priceFloorsConfigResolver;
    private final ActivitiesConfigResolver activitiesConfigResolver;
    private final JsonMerger jsonMerger;
    private final Account defaultAccount;

    public EnrichingApplicationSettings(boolean enforceValidAccount,
                                        String defaultAccountConfig,
                                        ApplicationSettings delegate,
                                        PriceFloorsConfigResolver priceFloorsConfigResolver,
                                        ActivitiesConfigResolver activitiesConfigResolver,
                                        JsonMerger jsonMerger,
                                        JacksonMapper mapper) {

        this.enforceValidAccount = enforceValidAccount;
        this.priceFloorsConfigResolver = Objects.requireNonNull(priceFloorsConfigResolver);
        this.activitiesConfigResolver = Objects.requireNonNull(activitiesConfigResolver);
        this.delegate = Objects.requireNonNull(delegate);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);

        this.defaultAccount = parseAccount(defaultAccountConfig, mapper);
    }

    private static Account parseAccount(String accountConfig, JacksonMapper mapper) {
        try {
            return StringUtils.isNotBlank(accountConfig)
                    ? mapper.decodeValue(accountConfig, Account.class)
                    : null;
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Could not parse default account configuration", e);
        }
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        if (StringUtils.isNotBlank(accountId)) {
            return delegate.getAccountById(accountId, timeout)
                    .map(this::mergeAccounts)
                    .map(account -> priceFloorsConfigResolver.resolve(account, extractDefaultPriceFloors()))
                    .map(activitiesConfigResolver::resolve)
                    .recover(throwable -> recoverIfNeeded(throwable, accountId));
        }

        return recoverIfNeeded(new PreBidException("Unauthorized account: account id is empty"), StringUtils.EMPTY);
    }

    private Account mergeAccounts(Account account) {
        return defaultAccount == null
                ? account
                : jsonMerger.merge(account, defaultAccount, Account.class);
    }

    private AccountPriceFloorsConfig extractDefaultPriceFloors() {
        return Optional.ofNullable(defaultAccount)
                .map(Account::getAuction)
                .map(AccountAuctionConfig::getPriceFloors)
                .orElse(null);
    }

    private Future<Account> recoverIfNeeded(Throwable throwable, String accountId) {
        // In case of invalid account return failed future
        return enforceValidAccount
                ? Future.failedFuture(throwable)
                : Future.succeededFuture(mergeAccounts(Account.empty(accountId)));
    }

    @Override
    public Future<StoredDataResult<String>> getStoredData(String accountId,
                                                          Set<String> requestIds,
                                                          Set<String> impIds,
                                                          Timeout timeout) {

        return delegate.getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult<String>> getAmpStoredData(String accountId,
                                                             Set<String> requestIds,
                                                             Set<String> impIds,
                                                             Timeout timeout) {

        return delegate.getAmpStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult<String>> getVideoStoredData(String accountId,
                                                               Set<String> requestIds,
                                                               Set<String> impIds,
                                                               Timeout timeout) {

        return delegate.getVideoStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult<Profile>> getProfiles(String accountId,
                                                         Set<String> requestIds,
                                                         Set<String> impIds,
                                                         Timeout timeout) {

        return delegate.getProfiles(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return delegate.getStoredResponses(responseIds, timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return delegate.getCategories(primaryAdServer, publisher, timeout);
    }
}

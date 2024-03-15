package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.activity.utils.AccountActivitiesConfigurationUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EnrichingApplicationSettings implements ApplicationSettings {

    private static final ConditionalLogger conditionalLogger =
            new ConditionalLogger(LoggerFactory.getLogger(EnrichingApplicationSettings.class));

    private final boolean enforceValidAccount;
    private final double logSamplingRate;
    private final ApplicationSettings delegate;
    private final PriceFloorsConfigResolver priceFloorsConfigResolver;
    private final JsonMerger jsonMerger;

    private final Account defaultAccount;

    public EnrichingApplicationSettings(boolean enforceValidAccount,
                                        double logSamplingRate,
                                        Account defaultAccount,
                                        ApplicationSettings delegate,
                                        PriceFloorsConfigResolver priceFloorsConfigResolver,
                                        JsonMerger jsonMerger) {

        this.enforceValidAccount = enforceValidAccount;
        this.logSamplingRate = logSamplingRate;
        this.delegate = Objects.requireNonNull(delegate);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.priceFloorsConfigResolver = Objects.requireNonNull(priceFloorsConfigResolver);
        this.defaultAccount = Objects.requireNonNull(defaultAccount);
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return delegate.getAccountById(accountId, timeout)
                .compose(priceFloorsConfigResolver::updateFloorsConfig)
                .map(this::mergeAccounts)
                .map(this::validateAndModifyAccount)
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
        return jsonMerger.merge(account, defaultAccount, Account.class);
    }

    private Account validateAndModifyAccount(Account account) {
        if (AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account)) {
            conditionalLogger.warn(
                    "Activity configuration for account %s contains conditional rule with empty array."
                            .formatted(account.getId()),
                    logSamplingRate);

            final AccountPrivacyConfig accountPrivacyConfig = account.getPrivacy();
            return account.toBuilder()
                    .privacy(accountPrivacyConfig.toBuilder()
                            .activities(AccountActivitiesConfigurationUtils
                                    .removeInvalidRules(accountPrivacyConfig.getActivities()))
                            .build())
                    .build();
        }

        return account;
    }

    private Future<Account> recoverIfNeeded(Throwable throwable, String accountId) {
        // In case of invalid account return failed future
        return !enforceValidAccount
                ? Future.succeededFuture(mergeAccounts(Account.empty(accountId)))
                : Future.failedFuture(throwable);
    }
}

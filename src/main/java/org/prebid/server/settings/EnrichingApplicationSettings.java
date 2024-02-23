package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.utils.AccountActivitiesConfigurationUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.floors.PriceFloorsConfigValidator;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
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
    private final PriceFloorsConfigValidator priceFloorsConfigValidator;
    private final JsonMerger jsonMerger;
    private final Account defaultAccount;

    public EnrichingApplicationSettings(boolean enforceValidAccount,
                                        double logSamplingRate,
                                        String defaultAccountConfig,
                                        ApplicationSettings delegate,
                                        PriceFloorsConfigValidator priceFloorsConfigValidator,
                                        JsonMerger jsonMerger,
                                        JacksonMapper mapper) {

        this.enforceValidAccount = enforceValidAccount;
        this.logSamplingRate = logSamplingRate;
        this.delegate = Objects.requireNonNull(delegate);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.priceFloorsConfigValidator = Objects.requireNonNull(priceFloorsConfigValidator);

        this.defaultAccount = parseAccount(defaultAccountConfig, mapper);
    }

    private static Account parseAccount(String accountConfig, JacksonMapper mapper) {
        try {
            final Account account = StringUtils.isNotBlank(accountConfig)
                    ? mapper.decodeValue(accountConfig, Account.class)
                    : Account.builder().build();

            return account != null ? account : Account.builder().build();
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Could not parse default account configuration", e);
        }
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        if (StringUtils.isNotBlank(accountId)) {
            return delegate.getAccountById(accountId, timeout)
                    .map(this::mergeAccounts)
                    .map(priceFloorsConfigValidator::validate)
                    .map(this::validateAndModifyAccount)
                    .recover(throwable -> recoverIfNeeded(throwable, accountId));
        }

        final String blankAccountId = StringUtils.EMPTY;
        return recoverIfNeeded(new PreBidException("Unauthorized account id: " + blankAccountId), blankAccountId);
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
                    .privacy(AccountPrivacyConfig.of(
                            accountPrivacyConfig.getGdpr(),
                            accountPrivacyConfig.getCcpa(),
                            accountPrivacyConfig.getDsa(),
                            AccountActivitiesConfigurationUtils
                                    .removeInvalidRules(accountPrivacyConfig.getActivities()),
                            accountPrivacyConfig.getModules()))
                    .build();
        }

        return account;
    }

    private Future<Account> recoverIfNeeded(Throwable throwable, String accountId) {
        // In case of invalid account return failed future
        return enforceValidAccount
                ? Future.failedFuture(throwable)
                : Future.succeededFuture(mergeAccounts(Account.empty(accountId)));
    }
}

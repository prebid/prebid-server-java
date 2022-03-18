package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.floors.PriceFloorsConfigResolver;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EnrichingApplicationSettings implements ApplicationSettings {

    private final ApplicationSettings delegate;
    private final PriceFloorsConfigResolver priceFloorsConfigResolver;
    private final JsonMerger jsonMerger;
    private final Account defaultAccount;

    public EnrichingApplicationSettings(String defaultAccountConfig,
                                        ApplicationSettings delegate,
                                        PriceFloorsConfigResolver priceFloorsConfigResolver,
                                        JsonMerger jsonMerger) {

        this.delegate = Objects.requireNonNull(delegate);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.priceFloorsConfigResolver = Objects.requireNonNull(priceFloorsConfigResolver);

        this.defaultAccount = parseAccount(defaultAccountConfig);
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        final Future<Account> accountFuture = delegate.getAccountById(accountId, timeout)
                .compose(priceFloorsConfigResolver::updateFloorsConfig);

        if (defaultAccount == null) {
            return accountFuture;
        }

        return accountFuture
                .map(this::mergeAccounts)
                .otherwise(mergeAccounts(Account.empty(accountId)));
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

    private static Account parseAccount(String accountConfig) {
        try {
            final Account account = StringUtils.isNotBlank(accountConfig)
                    ? new ObjectMapper().readValue(accountConfig, Account.class)
                    : null;

            return isNotEmpty(account) ? account : null;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not parse default account configuration", e);
        }
    }

    private static boolean isNotEmpty(Account account) {
        return account != null && !account.equals(Account.builder().build());
    }

    private Account mergeAccounts(Account account) {
        return jsonMerger.merge(account, defaultAccount, Account.class);
    }
}

package org.prebid.server.metric;

import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountMetricsConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AccountMetricsVerbosityResolver {

    private final AccountMetricsVerbosityLevel defaultVerbosity;
    private final Map<String, AccountMetricsVerbosityLevel> accountVerbosityLevels;

    public AccountMetricsVerbosityResolver(AccountMetricsVerbosityLevel defaultVerbosity,
                                           List<String> basicVerbosity,
                                           List<String> detailedVerbosity) {

        this.defaultVerbosity = Objects.requireNonNull(defaultVerbosity);

        this.accountVerbosityLevels = new HashMap<>();
        Objects.requireNonNull(basicVerbosity)
                .forEach(accountId -> accountVerbosityLevels.put(accountId, AccountMetricsVerbosityLevel.basic));
        Objects.requireNonNull(detailedVerbosity)
                .forEach(accountId -> accountVerbosityLevels.put(accountId, AccountMetricsVerbosityLevel.detailed));
    }

    public AccountMetricsVerbosityLevel forAccount(Account account) {
        final AccountMetricsConfig metricsConfig = account.getMetrics();
        final AccountMetricsVerbosityLevel accountVerbosity = metricsConfig != null
                ? metricsConfig.getVerbosityLevel()
                : null;
        return accountVerbosity != null
                ? accountVerbosity
                : accountVerbosityLevels.getOrDefault(account.getId(), defaultVerbosity);
    }
}

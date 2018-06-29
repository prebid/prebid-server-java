package org.prebid.server.metric;

import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AccountMetricsVerbosity {

    private final AccountMetricsVerbosityLevel defaultVerbosity;
    private final Map<String, AccountMetricsVerbosityLevel> accountVerbosityLevels;

    public AccountMetricsVerbosity(AccountMetricsVerbosityLevel defaultVerbosity, List<String> basicVerbosity,
                                   List<String> detailedVerbosity) {
        this.defaultVerbosity = Objects.requireNonNull(defaultVerbosity);

        this.accountVerbosityLevels = new HashMap<>();
        Objects.requireNonNull(basicVerbosity)
                .forEach(accountId -> accountVerbosityLevels.put(accountId, AccountMetricsVerbosityLevel.basic));
        Objects.requireNonNull(detailedVerbosity)
                .forEach(accountId -> accountVerbosityLevels.put(accountId, AccountMetricsVerbosityLevel.detailed));
    }

    public AccountMetricsVerbosityLevel forAccount(String accountId) {
        return accountVerbosityLevels.getOrDefault(accountId, defaultVerbosity);
    }
}

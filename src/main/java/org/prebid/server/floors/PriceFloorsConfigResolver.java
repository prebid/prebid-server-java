package org.prebid.server.floors;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.EnrichingApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;
import org.prebid.server.util.ObjectUtil;

import java.util.Objects;

public class PriceFloorsConfigResolver {

    private static final Logger logger = LoggerFactory.getLogger(EnrichingApplicationSettings.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final int MIN_MAX_AGE_SEC_VALUE = 600;
    private static final int MIN_PERIODIC_SEC_VALUE = 300;
    private static final int MIN_TIMEOUT_MS_VALUE = 10;
    private static final int MAX_TIMEOUT_MS_VALUE = 10_000;
    private static final int MIN_RULES_VALUE = 0;
    private static final int MIN_FILE_SIZE_VALUE = 0;
    private static final int MAX_AGE_SEC_VALUE = Integer.MAX_VALUE;
    private static final int MAX_RULES_VALUE = Integer.MAX_VALUE;
    private static final int MAX_FILE_SIZE_VALUE = Integer.MAX_VALUE;
    private static final int MIN_ENFORCE_RATE_VALUE = 0;
    private static final int MAX_ENFORCE_RATE_VALUE = 100;
    private static final long DEFAULT_MAX_AGE_SEC_VALUE = 86400L;

    private final Account defaultAccount;
    private final Metrics metrics;
    private final AccountPriceFloorsConfig defaultFloorsConfig;

    public PriceFloorsConfigResolver(String defaultAccountConfig, Metrics metrics, JacksonMapper mapper) {
        this.defaultAccount = parseAccount(defaultAccountConfig, mapper);
        this.defaultFloorsConfig = getFloorsConfig(defaultAccount);
        this.metrics = Objects.requireNonNull(metrics);
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

    private static AccountPriceFloorsConfig getFloorsConfig(Account account) {
        final AccountAuctionConfig auctionConfig = ObjectUtil.getIfNotNull(account, Account::getAuction);

        return ObjectUtil.getIfNotNull(auctionConfig, AccountAuctionConfig::getPriceFloors);
    }

    private static boolean isNotEmpty(Account account) {
        return account != null && !account.equals(Account.builder().build());
    }

    public Future<Account> updateFloorsConfig(Account account) {
        try {
            validatePriceFloorConfig(account, defaultFloorsConfig);
            return Future.succeededFuture(account);
        } catch (PreBidException e) {
            final String message =
                    String.format("Account with id '%s' has invalid config: %s", account.getId(), e.getMessage());
            final String accountId = ObjectUtil.getIfNotNull(account, Account::getId);
            if (StringUtils.isNotBlank(accountId)) {
                metrics.updateAlertsConfigFailed(account.getId(), MetricName.price_floors);
            }
            conditionalLogger.error(message, 0.01d);
        }

        return Future.succeededFuture(fallbackToDefaultConfig(account));
    }

    private static void validatePriceFloorConfig(Account account, AccountPriceFloorsConfig defaultFloorsConfig) {
        final AccountPriceFloorsConfig floorsConfig = getFloorsConfig(account);
        if (floorsConfig == null) {
            return;
        }
        final Integer accountEnforceRate = floorsConfig.getEnforceFloorsRate();
        final Integer enforceFloorsRate = accountEnforceRate != null
                ? accountEnforceRate
                : ObjectUtil.getIfNotNull(defaultFloorsConfig, AccountPriceFloorsConfig::getEnforceFloorsRate);
        if (enforceFloorsRate != null
                && isNotInRange(enforceFloorsRate, MIN_ENFORCE_RATE_VALUE, MAX_ENFORCE_RATE_VALUE)) {
            throw new PreBidException(
                    invalidPriceFloorsPropertyMessage("enforce-floors-rate", enforceFloorsRate));
        }
        final AccountPriceFloorsFetchConfig fetchConfig =
                ObjectUtil.getIfNotNull(floorsConfig, AccountPriceFloorsConfig::getFetch);
        final AccountPriceFloorsFetchConfig defaultFetchConfig =
                ObjectUtil.getIfNotNull(defaultFloorsConfig, AccountPriceFloorsConfig::getFetch);

        validatePriceFloorsFetchConfig(fetchConfig, defaultFetchConfig);
    }

    private static void validatePriceFloorsFetchConfig(AccountPriceFloorsFetchConfig fetchConfig,
                                                       AccountPriceFloorsFetchConfig defaultFetchConfig) {
        if (fetchConfig == null) {
            return;
        }

        final Long accountMaxAgeSec = fetchConfig.getMaxAgeSec();
        final Long defaultMaxAgeSec =
                ObjectUtil.getIfNotNull(defaultFetchConfig, AccountPriceFloorsFetchConfig::getMaxAgeSec);
        final long maxAgeSec = accountMaxAgeSec != null
                ? accountMaxAgeSec
                : defaultMaxAgeSec != null ? defaultMaxAgeSec : DEFAULT_MAX_AGE_SEC_VALUE;
        if (isNotInRange(maxAgeSec, MIN_MAX_AGE_SEC_VALUE, MAX_AGE_SEC_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-age-sec", maxAgeSec));
        }

        final Long accountPeriodicSec = fetchConfig.getPeriodSec();
        final Long periodicSec = accountPeriodicSec != null
                ? accountPeriodicSec
                : ObjectUtil.getIfNotNull(defaultFetchConfig, AccountPriceFloorsFetchConfig::getPeriodSec);
        if (periodicSec != null && isNotInRange(periodicSec, MIN_PERIODIC_SEC_VALUE, maxAgeSec)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("period-sec", periodicSec));
        }

        final Long accountTimeout = fetchConfig.getTimeout();
        final Long timeout = accountTimeout != null
                ? accountTimeout
                : ObjectUtil.getIfNotNull(defaultFetchConfig, AccountPriceFloorsFetchConfig::getTimeout);
        if (timeout != null && isNotInRange(timeout, MIN_TIMEOUT_MS_VALUE, MAX_TIMEOUT_MS_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("timeout-ms", timeout));
        }

        final Long accountMaxRules = fetchConfig.getMaxRules();
        final Long maxRules = accountMaxRules != null
                ? accountMaxRules
                : ObjectUtil.getIfNotNull(defaultFetchConfig, AccountPriceFloorsFetchConfig::getMaxRules);
        if (maxRules != null && isNotInRange(maxRules, MIN_RULES_VALUE, MAX_RULES_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-rules", maxRules));
        }

        final Long accountMaxFileSize = fetchConfig.getMaxFileSize();
        final Long maxFileSize = accountMaxFileSize != null
                ? accountMaxFileSize
                : ObjectUtil.getIfNotNull(defaultFetchConfig, AccountPriceFloorsFetchConfig::getMaxFileSize);
        if (maxFileSize != null && isNotInRange(maxFileSize, MIN_FILE_SIZE_VALUE, MAX_FILE_SIZE_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-file-size-kb", maxFileSize));
        }
    }

    private static boolean isNotInRange(long number, long min, long max) {
        return Math.max(min, number) != Math.min(number, max);
    }

    private static String invalidPriceFloorsPropertyMessage(String property, Object value) {
        return String.format("Invalid price-floors property '%s', value passed: %s", property, value);
    }

    private Account fallbackToDefaultConfig(Account account) {
        final AccountAuctionConfig auctionConfig = account.getAuction();
        final AccountAuctionConfig defaultAuctionConfig = ObjectUtil.getIfNotNull(defaultAccount, Account::getAuction);
        final AccountPriceFloorsConfig defaultPriceFloorsConfig =
                ObjectUtil.getIfNotNull(defaultAuctionConfig, AccountAuctionConfig::getPriceFloors);

        return account.toBuilder()
                .auction(auctionConfig.toBuilder().priceFloors(defaultPriceFloorsConfig).build())
                .build();
    }
}

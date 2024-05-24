package org.prebid.server.floors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
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

    private final Metrics metrics;

    public PriceFloorsConfigResolver(Metrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    public Account resolve(Account account, AccountPriceFloorsConfig fallbackPriceFloorConfig) {
        try {
            validatePriceFloorConfig(account);
            return account;
        } catch (PreBidException e) {
            final String message = "Account with id '%s' has invalid config: %s"
                    .formatted(account.getId(), e.getMessage());
            final String accountId = ObjectUtil.getIfNotNull(account, Account::getId);
            if (StringUtils.isNotBlank(accountId)) {
                metrics.updateAlertsConfigFailed(account.getId(), MetricName.price_floors);
            }
            conditionalLogger.error(message, 0.01d);
        }

        return account.toBuilder()
                .auction(account.getAuction().toBuilder().priceFloors(fallbackPriceFloorConfig).build())
                .build();
    }

    private static void validatePriceFloorConfig(Account account) {
        final AccountPriceFloorsConfig floorsConfig = getFloorsConfig(account);
        if (floorsConfig == null) {
            return;
        }

        final Integer enforceRate = floorsConfig.getEnforceFloorsRate();
        if (enforceRate != null && isNotInRange(enforceRate, MIN_ENFORCE_RATE_VALUE, MAX_ENFORCE_RATE_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("enforce-floors-rate", enforceRate));
        }

        final AccountPriceFloorsFetchConfig fetchConfig =
                ObjectUtil.getIfNotNull(floorsConfig, AccountPriceFloorsConfig::getFetch);

        validatePriceFloorsFetchConfig(fetchConfig);
    }

    private static AccountPriceFloorsConfig getFloorsConfig(Account account) {
        final AccountAuctionConfig auctionConfig = ObjectUtil.getIfNotNull(account, Account::getAuction);

        return ObjectUtil.getIfNotNull(auctionConfig, AccountAuctionConfig::getPriceFloors);
    }

    private static void validatePriceFloorsFetchConfig(AccountPriceFloorsFetchConfig fetchConfig) {
        if (fetchConfig == null) {
            return;
        }

        final long maxAgeSec = ObjectUtils.defaultIfNull(fetchConfig.getMaxAgeSec(), DEFAULT_MAX_AGE_SEC_VALUE);
        if (isNotInRange(maxAgeSec, MIN_MAX_AGE_SEC_VALUE, MAX_AGE_SEC_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-age-sec", maxAgeSec));
        }

        final Long periodicSec = fetchConfig.getPeriodSec();
        if (periodicSec != null && isNotInRange(periodicSec, MIN_PERIODIC_SEC_VALUE, maxAgeSec)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("period-sec", periodicSec));
        }

        final Long timeout = fetchConfig.getTimeout();
        if (timeout != null && isNotInRange(timeout, MIN_TIMEOUT_MS_VALUE, MAX_TIMEOUT_MS_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("timeout-ms", timeout));
        }

        final Long maxRules = fetchConfig.getMaxRules();
        if (maxRules != null && isNotInRange(maxRules, MIN_RULES_VALUE, MAX_RULES_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-rules", maxRules));
        }

        final Long maxFileSize = fetchConfig.getMaxFileSize();
        if (maxFileSize != null && isNotInRange(maxFileSize, MIN_FILE_SIZE_VALUE, MAX_FILE_SIZE_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-file-size-kb", maxFileSize));
        }
    }

    private static boolean isNotInRange(long number, long min, long max) {
        return Math.max(min, number) != Math.min(number, max);
    }

    private static String invalidPriceFloorsPropertyMessage(String property, Object value) {
        return "Invalid price-floors property '%s', value passed: %s".formatted(property, value);
    }
}

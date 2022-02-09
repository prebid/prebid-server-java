package org.prebid.server.validation;

import io.vertx.core.Future;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;
import org.prebid.server.util.ObjectUtil;

public class AccountValidator {

    private static final int MIN_MAX_AGE_SEC_VALUE = 600;
    private static final int MIN_AGE_SEC_VALUE = MIN_MAX_AGE_SEC_VALUE;
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

    public Future<Account> validateAccountConfig(Account account) {
        try {
            validatePriceFloorConfig(account);
        } catch (PreBidException e) {
            return Future.failedFuture(new InvalidAccountConfigException(
                    String.format("Account with id '%s' has invalid config: %s", account.getId(), e.getMessage())));
        }

        return Future.succeededFuture(account);
    }

    private static void validatePriceFloorConfig(Account account) {
        final AccountAuctionConfig accountAuctionConfig = ObjectUtil.getIfNotNull(account, Account::getAuction);
        final AccountPriceFloorsConfig floorsConfig =
                ObjectUtil.getIfNotNull(accountAuctionConfig, AccountAuctionConfig::getPriceFloors);
        if (floorsConfig == null) {
            return;
        }
        final int enforceFloorsRate = floorsConfig.getEnforceFloorsRate();
        if (isNotInRange(enforceFloorsRate, MIN_ENFORCE_RATE_VALUE, MAX_ENFORCE_RATE_VALUE)) {
            throw new PreBidException(
                    invalidPriceFloorsPropertyMessage("enforce-floors-rate", enforceFloorsRate));
        }
        final AccountPriceFloorsFetchConfig fetchConfig =
                ObjectUtil.getIfNotNull(floorsConfig, AccountPriceFloorsConfig::getFetch);

        validatePriceFloorsFetchConfig(fetchConfig);
    }

    private static void validatePriceFloorsFetchConfig(AccountPriceFloorsFetchConfig fetchConfig) {
        if (fetchConfig == null) {
            return;
        }
        final long maxAgeSec = fetchConfig.getMaxAgeSec();
        if (isNotInRange(maxAgeSec, MIN_AGE_SEC_VALUE, MAX_AGE_SEC_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-age-sec", maxAgeSec));
        }
        final long periodicSec = fetchConfig.getPeriodSec();
        if (isNotInRange(periodicSec, MIN_PERIODIC_SEC_VALUE, maxAgeSec)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("period-sec", periodicSec));
        }
        final Long timeout = fetchConfig.getTimeout();
        if (isNotInRange(timeout, MIN_TIMEOUT_MS_VALUE, MAX_TIMEOUT_MS_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("timeout-ms", timeout));
        }
        final long maxRules = fetchConfig.getMaxRules();
        if (isNotInRange(maxRules, MIN_RULES_VALUE, MAX_RULES_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-rules", maxRules));
        }
        final long maxFileSize = fetchConfig.getMaxFileSize();
        if (isNotInRange(maxFileSize, MIN_FILE_SIZE_VALUE, MAX_FILE_SIZE_VALUE)) {
            throw new PreBidException(invalidPriceFloorsPropertyMessage("max-file-size-kb", maxFileSize));
        }
    }

    private static boolean isNotInRange(long number, long min, long max) {
        return Math.max(min, number) != Math.min(number, max);
    }

    private static String invalidPriceFloorsPropertyMessage(String property, Object value) {
        return String.format("Invalid price-floors property '%s', value passed: %s", property, value);
    }
}

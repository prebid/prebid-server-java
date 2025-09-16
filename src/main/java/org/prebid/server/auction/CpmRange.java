package org.prebid.server.auction;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountAuctionBidRoundingMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Class for price operating with rules defined in {@link PriceGranularity}
 */
public class CpmRange {

    private static final Locale LOCALE = Locale.US;
    private static final int DEFAULT_PRECISION = 2;

    private CpmRange() {
    }

    /**
     * Rounding price by specified rules defined in {@link PriceGranularity} object and returns it in string format
     */
    public static String fromCpm(BigDecimal cpm, PriceGranularity priceGranularity, Account account) {
        final BigDecimal value = fromCpmAsNumber(cpm, priceGranularity, account);
        return value != null ? format(value, priceGranularity.getPrecision()) : StringUtils.EMPTY;
    }

    /**
     * Formats {@link BigDecimal} value with a given precision and return its string representation.
     */
    public static String format(BigDecimal value, Integer precision) {
        return numberFormat(ObjectUtils.defaultIfNull(precision, DEFAULT_PRECISION)).format(value);
    }

    private static NumberFormat numberFormat(int precision) {
        final NumberFormat numberFormat = NumberFormat.getInstance(LOCALE);
        numberFormat.setRoundingMode(RoundingMode.FLOOR);
        numberFormat.setMaximumFractionDigits(precision);
        numberFormat.setMinimumFractionDigits(precision);
        return numberFormat;
    }

    /**
     * Rounding price by specified rules defined in {@link PriceGranularity} object and returns it in {@link BigDecimal}
     * format
     */
    public static BigDecimal fromCpmAsNumber(BigDecimal cpm, PriceGranularity priceGranularity, Account account) {
        if (cpm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        final BigDecimal rangeMax = priceGranularity.getRangesMax();
        if (cpm.compareTo(rangeMax) > 0) {
            return rangeMax;
        }

        BigDecimal min = BigDecimal.ZERO;
        BigDecimal increment = null;
        for (ExtGranularityRange range : priceGranularity.getRanges()) {
            final BigDecimal max = range.getMax();
            if (cpm.compareTo(max) <= 0) {
                increment = range.getIncrement();
                break;
            }

            min = max;
        }

        return increment != null ? calculate(cpm, min, increment, resolveRoundingMode(account)) : null;
    }

    private static BigDecimal calculate(BigDecimal cpm,
                                        BigDecimal min,
                                        BigDecimal increment,
                                        RoundingMode roundingMode) {

        return cpm
                .subtract(min)
                .divide(increment, 0, roundingMode)
                .multiply(increment)
                .add(min);
    }

    private static RoundingMode resolveRoundingMode(Account account) {
        final AccountAuctionBidRoundingMode accountRoundingMode = Optional.ofNullable(account)
                .map(Account::getAuction)
                .map(AccountAuctionConfig::getBidRounding)
                .orElse(AccountAuctionBidRoundingMode.DOWN);

        return switch (accountRoundingMode) {
            case DOWN -> RoundingMode.FLOOR;
            case UP -> RoundingMode.CEILING;
            case TRUE -> RoundingMode.HALF_UP;
            case TIMESPLIT -> ThreadLocalRandom.current().nextBoolean() ? RoundingMode.FLOOR : RoundingMode.CEILING;
        };
    }
}

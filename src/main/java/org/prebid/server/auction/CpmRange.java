package org.prebid.server.auction;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

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
    public static String fromCpm(BigDecimal cpm, PriceGranularity priceGranularity) {
        final BigDecimal value = fromCpmAsNumber(cpm, priceGranularity);
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
    public static BigDecimal fromCpmAsNumber(BigDecimal cpm, PriceGranularity priceGranularity) {
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

        return increment != null ? calculate(cpm, min, increment) : null;
    }

    private static BigDecimal calculate(BigDecimal cpm, BigDecimal min, BigDecimal increment) {
        return cpm
                .subtract(min)
                .divide(increment, 0, RoundingMode.FLOOR)
                .multiply(increment)
                .add(min);
    }
}

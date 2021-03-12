package org.prebid.server.auction;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Class for price operating with rules defined in {@link PriceGranularity}
 */
public class CpmRange {

    private static final Locale LOCALE = Locale.US;

    private CpmRange() {
    }

    /**
     * Rounding price by specified rules defined in {@link PriceGranularity} object and returns it in string format
     */
    public static String fromCpm(BigDecimal cpm, PriceGranularity priceGranularity) {
        final BigDecimal value = fromCpmAsNumber(cpm, priceGranularity);
        return value != null
                ? format(value, ObjectUtils.defaultIfNull(priceGranularity.getPrecision(), 2))
                : StringUtils.EMPTY;
    }

    /**
     * Formats {@link BigDecimal} value with a given precision and return it's string representation.
     */
    public static String format(BigDecimal value, Integer precision) {
        final String format = String.format("%%.%sf", precision);
        return String.format(LOCALE, format, value);
    }

    /**
     * Rounding price by specified rules defined in {@link PriceGranularity} object and returns it in {@link BigDecimal}
     * format
     */
    public static BigDecimal fromCpmAsNumber(BigDecimal cpm, PriceGranularity priceGranularity) {
        final BigDecimal rangeMax = priceGranularity.getRangesMax();
        if (cpm.compareTo(rangeMax) > 0) {
            return rangeMax;
        }
        final ExtGranularityRange range = findRangeFor(cpm, priceGranularity.getRanges());
        final BigDecimal increment = range != null ? range.getIncrement() : null;

        return increment != null ? cpm.divide(increment, 0, RoundingMode.FLOOR).multiply(increment) : null;
    }

    /**
     * Returns range cpm fits in.
     */
    private static ExtGranularityRange findRangeFor(BigDecimal cpm, List<ExtGranularityRange> ranges) {
        BigDecimal min = BigDecimal.ZERO;
        for (ExtGranularityRange range : ranges) {
            if (includes(cpm, min, range.getMax())) {
                return range;
            }
            min = range.getMax();
        }
        return null;
    }

    /**
     * Checks if cpm fits into the borders.
     */
    private static boolean includes(BigDecimal cpm, BigDecimal min, BigDecimal max) {
        return cpm.compareTo(min) >= 0 && cpm.compareTo(max) <= 0;
    }
}

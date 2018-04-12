package org.prebid.server.auction;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularityBucket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/**
 * Class for price operating with rules defined in {@link PriceGranularity}
 */
public class CpmBucket {

    private static final Locale LOCALE = Locale.US;

    private CpmBucket() {
    }

    /**
     * Rounding price by specified rules defined in {@link PriceGranularity} object and returns it in string format
     */
    public static String fromCpm(BigDecimal cpm, PriceGranularity priceGranularity) {
        final BigDecimal value = fromCpmAsNumber(cpm, priceGranularity);
        return value != null
                ? format(value, ObjectUtils.firstNonNull(priceGranularity.getPrecision(), 2))
                : StringUtils.EMPTY;
    }

    /**
     * Rounding price by specified rules defined in {@link PriceGranularity} object and returns it in {@link BigDecimal}
     * format
     */
    public static BigDecimal fromCpmAsNumber(BigDecimal cpm, PriceGranularity priceGranularity) {
        final BigDecimal bucketMax = priceGranularity.getBucketMax();
        if (cpm.compareTo(bucketMax) > 0) {
            return bucketMax;
        }
        return findBucketFor(cpm, priceGranularity)
                .map(ExtPriceGranularityBucket::getIncrement)
                .map(increment -> cpm.divide(increment, 0, RoundingMode.FLOOR).multiply(increment))
                .orElse(null);
    }

    /**
     * Returns bucket cpm fits in.
     */
    private static Optional<ExtPriceGranularityBucket> findBucketFor(BigDecimal cpm,
                                                                     PriceGranularity priceGranularity) {
        return priceGranularity.getBuckets().stream()
                .filter(bucket -> includes(cpm, bucket.getMin(), bucket.getMax()))
                .reduce((first, second) -> second);
    }

    /**
     * Checks if cpm fits into the borders.
     */
    private static boolean includes(BigDecimal cpm, BigDecimal min, BigDecimal max) {
        return cpm.compareTo(min) >= 0 && cpm.compareTo(max) <= 0;
    }

    /**
     * Formats {@link BigDecimal} value with a given precision and return it's string representation.
     */
    private static String format(BigDecimal value, Integer precision) {
        final String format = String.format("%%.%sf", precision);
        return String.format(LOCALE, format, value);
    }
}

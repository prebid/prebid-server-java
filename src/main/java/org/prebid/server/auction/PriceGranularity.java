package org.prebid.server.auction;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

/**
 * Describes the behavior for price granularity feature.
 */
public class PriceGranularity {

    enum PriceGranularityType {
        low, medium, med, high, auto, dense
    }

    private static final EnumMap<PriceGranularityType, PriceGranularity> STRING_TO_CUSTOM_PRICE_GRANULARITY =
            new EnumMap<>(PriceGranularityType.class);

    static {
        putStringPriceGranularity(PriceGranularityType.low, 2, range(5, 0.5));
        final ExtGranularityRange medRange = range(20, 0.1);
        putStringPriceGranularity(PriceGranularityType.medium, 2, medRange);
        putStringPriceGranularity(PriceGranularityType.med, 2, medRange);
        putStringPriceGranularity(PriceGranularityType.high, 2, range(20, 0.01));
        putStringPriceGranularity(PriceGranularityType.auto, 2,
                range(5, 0.05),
                range(10, 0.1),
                range(20, 0.5));
        putStringPriceGranularity(PriceGranularityType.dense, 2,
                range(3, 0.01),
                range(8, 0.05),
                range(20, 0.5));
    }

    static final PriceGranularity DEFAULT = STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.med);

    private List<ExtGranularityRange> ranges;
    private BigDecimal rangesMax;
    private Integer precision;

    private PriceGranularity(List<ExtGranularityRange> ranges, BigDecimal rangesMax, Integer precision) {
        this.ranges = ranges;
        this.rangesMax = rangesMax;
        this.precision = precision;
    }

    /**
     * Creates {@link PriceGranularity} from {@link ExtPriceGranularity}.
     */
    static PriceGranularity createFromExtPriceGranularity(ExtPriceGranularity extPriceGranularity) {
        return createFromRanges(extPriceGranularity.getPrecision(), extPriceGranularity.getRanges());
    }

    /**
     * Returns {@link PriceGranularity} by string representation if it is present in map, otherwise returns null.
     */
    static PriceGranularity createFromString(String stringPriceGranularity) {
        if (isValidStringPriceGranularityType(stringPriceGranularity)) {
            return STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.valueOf(stringPriceGranularity));
        } else {
            throw new PreBidException(String.format(
                    "Invalid string price granularity with value: %s", stringPriceGranularity));
        }
    }

    /**
     * Returns list of {@link ExtGranularityRange}s.
     */
    public List<ExtGranularityRange> getRanges() {
        return ranges;
    }

    /**
     * Returns max value among all ranges.
     */
    BigDecimal getRangesMax() {
        return rangesMax;
    }

    /**
     * Returns {@link PriceGranularity} precision.
     */
    public Integer getPrecision() {
        return precision;
    }

    /**
     * Creates {@link PriceGranularity} for string representation and puts it to
     * {@link EnumMap<PriceGranularityType, PriceGranularity>}.
     */
    private static void putStringPriceGranularity(PriceGranularityType type, Integer precision,
                                                  ExtGranularityRange... ranges) {
        STRING_TO_CUSTOM_PRICE_GRANULARITY.put(type,
                PriceGranularity.createFromRanges(precision, Arrays.asList(ranges)));
    }

    /**
     * Creates {@link PriceGranularity} from list of {@link ExtGranularityRange}s and validates it.
     */
    private static PriceGranularity createFromRanges(Integer precision, List<ExtGranularityRange> ranges) {
        if (CollectionUtils.isEmpty(ranges)) {
            throw new IllegalArgumentException("Ranges list cannot be null or empty");
        }

        final BigDecimal rangeMax = ranges.stream()
                .map(ExtGranularityRange::getMax)
                .max(BigDecimal::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Max value among all ranges was not found. Please check if ranges are valid"));

        return new PriceGranularity(ranges, rangeMax, precision);
    }

    /**
     * Creates {@link ExtGranularityRange`} from given precision, min, max and increment parameters.
     */
    private static ExtGranularityRange range(int max, double increment) {
        return ExtGranularityRange.of(BigDecimal.valueOf(max), BigDecimal.valueOf(increment));
    }

    /**
     * Checks if string price granularity is valid type.
     */
    private static boolean isValidStringPriceGranularityType(String stringPriceGranularity) {
        return EnumUtils.isValidEnum(PriceGranularityType.class, stringPriceGranularity);
    }
}

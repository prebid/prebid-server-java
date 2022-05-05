package org.prebid.server.auction;

import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Describes the behavior for price granularity feature.
 */
@NoArgsConstructor
public class PriceGranularity {

    enum PriceGranularityType {
        LOW, MEDIUM, MED, HIGH, AUTO, DENSE;

        public static boolean isValidEnum(String stringPriceGranularity) {
            return Arrays.stream(values())
                    .map(Enum::name)
                    .anyMatch(priceGranularityType -> priceGranularityType.equalsIgnoreCase(stringPriceGranularity));
        }

        public static PriceGranularityType getEnum(String stringPriceGranularity) {
            return Arrays.stream(values())
                    .filter(granularity -> granularity.name().equalsIgnoreCase(stringPriceGranularity))
                    .findFirst()
                    //should never occur
                    .orElseThrow(() -> new PreBidException(String.format(
                            "Invalid string price granularity with value: %s", stringPriceGranularity)));
        }
    }

    private static final EnumMap<PriceGranularityType, PriceGranularity> STRING_TO_CUSTOM_PRICE_GRANULARITY =
            new EnumMap<>(PriceGranularityType.class);

    static {
        putStringPriceGranularity(PriceGranularityType.LOW, 2, range(5, 0.5));
        final ExtGranularityRange medRange = range(20, 0.1);
        putStringPriceGranularity(PriceGranularityType.MEDIUM, 2, medRange);
        putStringPriceGranularity(PriceGranularityType.MED, 2, medRange);
        putStringPriceGranularity(PriceGranularityType.HIGH, 2, range(20, 0.01));
        putStringPriceGranularity(PriceGranularityType.AUTO, 2,
                range(5, 0.05),
                range(10, 0.1),
                range(20, 0.5));
        putStringPriceGranularity(PriceGranularityType.DENSE, 2,
                range(3, 0.01),
                range(8, 0.05),
                range(20, 0.5));
    }

    public static final PriceGranularity DEFAULT = STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.MED);

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
    public static PriceGranularity createFromExtPriceGranularity(ExtPriceGranularity extPriceGranularity) {
        return createFromRanges(extPriceGranularity.getPrecision(), extPriceGranularity.getRanges());
    }

    /**
     * Checks if string price granularity is valid type and create {@link PriceGranularityType} from string.
     * Returns {@link PriceGranularity} by string representation if it is present in map, otherwise returns null.
     */
    public static PriceGranularity createFromString(String stringPriceGranularity) {
        if (PriceGranularityType.isValidEnum(stringPriceGranularity)) {
            return STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.getEnum(stringPriceGranularity));
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
    public static PriceGranularity createFromRanges(Integer precision, List<ExtGranularityRange> ranges) {

        final BigDecimal rangeMax = CollectionUtils.emptyIfNull(ranges).stream()
                .filter(Objects::nonNull)
                .map(ExtGranularityRange::getMax)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Price granularity error: "
                                + "Max value among all ranges was not found. Please check if ranges are valid"));

        return new PriceGranularity(ranges, rangeMax, precision);
    }

    /**
     * Creates {@link ExtGranularityRange`} from given precision, min, max and increment parameters.
     */
    private static ExtGranularityRange range(int max, double increment) {
        return ExtGranularityRange.of(BigDecimal.valueOf(max), BigDecimal.valueOf(increment));
    }
}

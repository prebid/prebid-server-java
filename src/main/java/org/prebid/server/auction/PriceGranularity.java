package org.prebid.server.auction;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularityBucket;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
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
        putStringPriceGranularity(PriceGranularityType.low, bucket(2, 0, 5, 0.5));
        final ExtPriceGranularityBucket medBucket = bucket(2, 0, 20, 0.1);
        putStringPriceGranularity(PriceGranularityType.medium, medBucket);
        putStringPriceGranularity(PriceGranularityType.med, medBucket);
        putStringPriceGranularity(PriceGranularityType.high, bucket(2, 0, 20, 0.01));
        putStringPriceGranularity(PriceGranularityType.auto,
                bucket(2, 0, 5, 0.05),
                bucket(2, 5, 10, 0.1),
                bucket(2, 10, 20, 0.5));
        putStringPriceGranularity(PriceGranularityType.dense,
                bucket(2, 0, 3, 0.01),
                bucket(2, 3, 8, 0.05),
                bucket(2, 8, 20, 0.5));
    }

    /**
     * Creates {@link PriceGranularity} for string representation and puts it to
     * {@link EnumMap<PriceGranularityType, PriceGranularity>}
     */
    private static void putStringPriceGranularity(PriceGranularityType type, ExtPriceGranularityBucket... buckets) {
        STRING_TO_CUSTOM_PRICE_GRANULARITY.put(type,
                PriceGranularity.createFromBuckets(Arrays.asList(buckets)));
    }

    /**
     * Creates {@link ExtPriceGranularityBucket} from given precision, min, max and increment parameters.
     */
    private static ExtPriceGranularityBucket bucket(int precision, int min, int max, double increment) {
        return ExtPriceGranularityBucket.of(precision, BigDecimal.valueOf(min), BigDecimal.valueOf(max),
                BigDecimal.valueOf(increment));
    }

    public static final PriceGranularity DEFAULT = STRING_TO_CUSTOM_PRICE_GRANULARITY
            .get(PriceGranularityType.med);

    private List<ExtPriceGranularityBucket> buckets;
    private BigDecimal bucketMax;
    private Integer precision;

    private PriceGranularity(List<ExtPriceGranularityBucket> buckets, BigDecimal bucketMax, Integer precision) {
        this.buckets = buckets;
        this.bucketMax = bucketMax;
        this.precision = precision;
    }

    /**
     * Creates {@link PriceGranularity} from {@link List<ExtPriceGranularityBucket>} and validates it.
     */
    public static PriceGranularity createFromBuckets(List<ExtPriceGranularityBucket> buckets) {
        if (CollectionUtils.isEmpty(buckets)) {
            throw new IllegalArgumentException("Bucket list cannot be null or empty");
        }

        // case when min value is null is valid, so it should be replaced with previous bucket max value or with 0.0 if
        // it is first bucket in a list
        final List<ExtPriceGranularityBucket> updatedBuckets = updateBucketsMin(buckets);

        final BigDecimal bucketMax = updatedBuckets.stream()
                .map(ExtPriceGranularityBucket::getMax)
                .max(BigDecimal::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Max value among all buckets was not found. Please check if buckets ranges are valid"));

        return new PriceGranularity(updatedBuckets, bucketMax, buckets.get(0).getPrecision());
    }

    /**
     * Returns {@link PriceGranularity} by string representation if it is present in map, otherwise returns null
     */
    public static PriceGranularity createFromString(String stringPriceGranularity) {
        if (isValidStringPriceGranularityType(stringPriceGranularity)) {
            return STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.valueOf(stringPriceGranularity));
        } else {
            throw new PreBidException(String.format(
                    "Invalid string price granularity with value: %s", stringPriceGranularity));
        }
    }

    /**
     * Checks if string price granularity is valid type.
     */
    private static boolean isValidStringPriceGranularityType(String stringPriceGranularity) {
        return EnumUtils.isValidEnum(PriceGranularityType.class, stringPriceGranularity);
    }

    /**
     * Updates buckets min value if it was not defined in request and has null value. First bucket min will be replaced
     * with 0.0 and next buckets min will be replaced with previous max.
     */
    private static List<ExtPriceGranularityBucket> updateBucketsMin(List<ExtPriceGranularityBucket> buckets) {
        final List<ExtPriceGranularityBucket> updatedBuckets = new ArrayList<>();
        final Iterator<ExtPriceGranularityBucket> bucketIterator = buckets.iterator();

        // check and update first bucket
        ExtPriceGranularityBucket prevBucket = bucketIterator.next();
        updatedBuckets.add(prevBucket.getMin() == null
                ? ExtPriceGranularityBucket.of(prevBucket.getPrecision(), BigDecimal.ZERO, prevBucket.getMax(),
                prevBucket.getIncrement())
                : prevBucket);
        BigDecimal prevMax = prevBucket.getMax();

        // check and update rest buckets
        while (bucketIterator.hasNext()) {
            final ExtPriceGranularityBucket bucket = bucketIterator.next();
            updatedBuckets.add(bucket.getMin() == null ? ExtPriceGranularityBucket.of(bucket.getPrecision(), prevMax,
                    bucket.getMax(), bucket.getIncrement()) : bucket);
            prevMax = bucket.getMax();
        }
        return updatedBuckets;
    }

    /**
     * Returns {@link List<ExtPriceGranularityBucket>}
     */
    public List<ExtPriceGranularityBucket> getBuckets() {
        return buckets;
    }

    /**
     * Returns max value among all buckets
     */
    public BigDecimal getBucketMax() {
        return bucketMax;
    }

    /**
     * Returns {@link PriceGranularity} precision
     */
    public Integer getPrecision() {
        return precision;
    }
}

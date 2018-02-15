package org.rtb.vexing.auction;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

class CpmBucket {

    enum PriceGranularity {
        low, medium, med, high, auto, dense
    }

    private static final EnumMap<PriceGranularity, BucketConfig> PRICE_BUCKET_CONFIGS =
            new EnumMap<>(PriceGranularity.class);

    static {
        priceBucketConfig(PriceGranularity.low, config(0, 5, 0.5));
        final Bucket medConfig = config(0, 20, 0.1);
        priceBucketConfig(PriceGranularity.medium, medConfig);
        // Seems that PBS was written with medium = "med", so hacking that in
        priceBucketConfig(PriceGranularity.med, medConfig);
        priceBucketConfig(PriceGranularity.high, config(0, 20, 0.01));
        priceBucketConfig(PriceGranularity.auto,
                config(0, 5, 0.05),
                config(5, 10, 0.1),
                config(10, 20, 0.5));
        priceBucketConfig(PriceGranularity.dense,
                config(0, 3, 0.01),
                config(3, 8, 0.05),
                config(8, 20, 0.5));
    }

    private static void priceBucketConfig(PriceGranularity priceGranularity, Bucket... configs) {
        PRICE_BUCKET_CONFIGS.put(priceGranularity, new BucketConfig(Arrays.asList(configs)));
    }

    private static Bucket config(int min, int max, double increment) {
        return new Bucket(new BigDecimal(min), new BigDecimal(max), BigDecimal.valueOf(increment));
    }

    private CpmBucket() {
    }

    static String fromCpm(BigDecimal cpm, PriceGranularity priceGranularity) {
        final BigDecimal value = fromCpmAsNumber(cpm, priceGranularity);
        return value != null ? format(value) : StringUtils.EMPTY;
    }

    static BigDecimal fromCpmAsNumber(BigDecimal cpm, PriceGranularity priceGranularity) {
        final BucketConfig bucketConfig = PRICE_BUCKET_CONFIGS.get(priceGranularity);
        if (cpm.compareTo(bucketConfig.bucketMax) > 0) {
            return bucketConfig.bucketMax;
        }
        return bucketConfig.findBucketFor(cpm)
                .map(bucket -> bucket.increment)
                .map(increment -> cpm.divide(increment, 0, RoundingMode.FLOOR).multiply(increment))
                .orElse(null);
    }

    private static String format(BigDecimal value) {
        return String.format("%.2f", value);
    }

    private static class BucketConfig {
        final List<Bucket> buckets;
        final BigDecimal bucketMax;

        BucketConfig(List<Bucket> buckets) {
            this.buckets = buckets;
            bucketMax = buckets.stream().map(bucket -> bucket.max).max(BigDecimal::compareTo)
                    // supplier should be never invoked
                    .orElseThrow(() -> new IllegalArgumentException("Could not find max for cpm bucket"));
        }

        Optional<Bucket> findBucketFor(BigDecimal cpm) {
            return buckets.stream()
                    .filter(bucket -> bucket.includes(cpm))
                    .reduce((first, second) -> second);
        }
    }

    @AllArgsConstructor
    private static class Bucket {
        final BigDecimal min;
        final BigDecimal max;
        final BigDecimal increment;

        boolean includes(BigDecimal cpm) {
            return cpm.compareTo(min) >= 0 && cpm.compareTo(max) <= 0;
        }
    }
}

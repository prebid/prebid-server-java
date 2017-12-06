package org.rtb.vexing.auction;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class CpmBucket {

    private static final Logger logger = LoggerFactory.getLogger(CpmBucket.class);

    private static final Map<String, BucketConfig> PRICE_BUCKET_CONFIGS = new HashMap<>();

    static {
        priceBucketConfig("low", config(0, 5, 0.5));
        final Bucket medConfig = config(0, 20, 0.1);
        priceBucketConfig("medium", medConfig);
        // Seems that PBS was written with medium = "med", so hacking that in
        priceBucketConfig("med", medConfig);
        priceBucketConfig("high", config(0, 20, 0.01));
        priceBucketConfig("auto",
                config(0, 5, 0.05),
                config(5, 10, 0.1),
                config(10, 20, 0.5));
        priceBucketConfig("dense",
                config(0, 3, 0.01),
                config(3, 8, 0.05),
                config(8, 20, 0.5));
    }

    private static void priceBucketConfig(String priceGranularity, Bucket... configs) {
        PRICE_BUCKET_CONFIGS.put(priceGranularity, new BucketConfig(Arrays.asList(configs)));
    }

    private static Bucket config(int min, int max, double increment) {
        return new Bucket(new BigDecimal(min), new BigDecimal(max), BigDecimal.valueOf(increment));
    }

    private CpmBucket() {
    }

    static String fromCpm(BigDecimal cpm, String priceGranularity) {
        final String result;

        final BucketConfig bucketConfig = PRICE_BUCKET_CONFIGS.get(priceGranularity);
        if (bucketConfig == null) {
            logger.error("Price bucket granularity error: '{0}' is not a recognized granularity", priceGranularity);
            result = StringUtils.EMPTY;
        } else if (cpm.compareTo(bucketConfig.bucketMax) > 0) {
            result = format(bucketConfig.bucketMax);
        } else {
            result = bucketConfig.findBucketFor(cpm)
                    .map(bucket -> bucket.increment)
                    .map(increment -> format(cpm.divide(increment, 0, RoundingMode.FLOOR).multiply(increment)))
                    .orElse(StringUtils.EMPTY);
        }

        return result;
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

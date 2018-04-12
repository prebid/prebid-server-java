package org.prebid.server.auction;

import org.junit.Test;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularityBucket;

import java.math.BigDecimal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.*;

public class PriceGranularityTest {

    @Test
    public void createFromFromBucketsShouldThrowIllegalArgumentsExceptionIfBucketsListNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> PriceGranularity.createFromBuckets(null));
    }

    @Test
    public void createFromFromBucketsShouldThrowIllegalArgumentsExceptionIfBucketsListIsEmpty() {
        assertThatIllegalArgumentException().isThrownBy(() -> PriceGranularity.createFromBuckets(emptyList()));
    }

    @Test
    public void createFromStringShouldThrowPrebidExceptionIfInvalidStringType() {
        assertThatExceptionOfType(PreBidException.class).isThrownBy(()-> PriceGranularity.createFromString("invalid"));
    }

    @Test
    public void createFromBucketsShouldReplaceFirstBucketMinNullValueWithZero() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromBuckets(singletonList(
                ExtPriceGranularityBucket.of(0, null, BigDecimal.valueOf(3), BigDecimal.valueOf(0.01))));

        // then
        assertThat(priceGranularity.getBuckets()).containsExactly(
                ExtPriceGranularityBucket.of(0, BigDecimal.ZERO, BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)));
    }

    @Test
    public void createFromBucketsShouldReplaceNullMinValuesWithPrevBucketMaxValues() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromBuckets(asList(
                ExtPriceGranularityBucket.of(0, null, BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtPriceGranularityBucket.of(0, null, BigDecimal.valueOf(6), BigDecimal.valueOf(0.01)),
                ExtPriceGranularityBucket.of(0, null, BigDecimal.valueOf(10), BigDecimal.valueOf(0.01))));

        // then
        assertThat(priceGranularity.getBuckets()).containsExactly(
                ExtPriceGranularityBucket.of(0, BigDecimal.ZERO, BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtPriceGranularityBucket.of(0, BigDecimal.valueOf(3), BigDecimal.valueOf(6), BigDecimal.valueOf(0.01)),
                ExtPriceGranularityBucket.of(0, BigDecimal.valueOf(6), BigDecimal.valueOf(10),
                        BigDecimal.valueOf(0.01)));
    }

    @Test
    public void createCustomPriceGranularityByStringLow() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("low");

        // then
        assertThat(priceGranularity.getBucketMax()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(priceGranularity.getBuckets()).containsOnly(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)));
    }

    @Test
    public void createCustomPriceGranularityByStringMedAndMedium() {
        // given and when
        final PriceGranularity priceGranularityMed = PriceGranularity.createFromString("med");
        final PriceGranularity priceGranularityMedium = PriceGranularity.createFromString("medium");

        // then
        assertThat(priceGranularityMed.getBucketMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularityMedium.getBucketMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularityMed.getBuckets()).containsOnly(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(20), BigDecimal.valueOf(0.1)));
        assertThat(priceGranularityMedium.getBuckets()).containsOnly(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(20), BigDecimal.valueOf(0.1)));
    }

    @Test
    public void createCustomPriceGranularityByStringHigh() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("high");

        // then
        assertThat(priceGranularity.getBucketMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularity.getBuckets()).containsOnly(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(20), BigDecimal.valueOf(0.01)));
    }

    @Test
    public void createCustomPriceGranularityByStringAuto() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("auto");

        // then
        assertThat(priceGranularity.getBucketMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularity.getBuckets()).containsExactly(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0.05)),
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(5), BigDecimal.valueOf(10), BigDecimal.valueOf(0.1)),
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(10), BigDecimal.valueOf(20), BigDecimal.valueOf(0.5))
        );
    }

    @Test
    public void createCustomPriceGranularityByStringDense() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("dense");

        // then
        assertThat(priceGranularity.getBucketMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularity.getBuckets()).containsExactly(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(3), BigDecimal.valueOf(8), BigDecimal.valueOf(0.05)),
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(8), BigDecimal.valueOf(20), BigDecimal.valueOf(0.5)));
    }

    @Test
    public void createFromBucketsShouldReturnCorrectPriceGranularity() {

        //given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromBuckets(asList(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(3), BigDecimal.valueOf(8), BigDecimal.valueOf(0.05))));

        // then
        assertThat(priceGranularity.getBucketMax()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(priceGranularity.getBuckets()).containsExactly(
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0), BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(3), BigDecimal.valueOf(8), BigDecimal.valueOf(0.05)));
    }
}

package org.prebid.server.auction;

import org.junit.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularityBucket;

import java.math.BigDecimal;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class CpmBucketTest {

    @Test
    public void fromCpmShouldReturnMaxBucketIfCpmExceedsIt() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(21), PriceGranularity.createFromString("auto")))
                .isEqualTo("20.00");
    }

    @Test
    public void fromCpmShouldReturnPriceWithCorrectPrecision() {
        // given
        final PriceGranularity priceGranularity = PriceGranularity.createFromBuckets(singletonList(
                ExtPriceGranularityBucket.of(1, BigDecimal.valueOf(0), BigDecimal.valueOf(10), BigDecimal.valueOf(0.1))));

        // when
        final String cpm = CpmBucket.fromCpm(BigDecimal.valueOf(5.1245), priceGranularity);

        // then
        assertThat(cpm).isEqualTo("5.1");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenLowGranularity() {
        assertThat(
                CpmBucket.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("low")))
                .isEqualTo("3.50");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenMedGranularity() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("med")))
                .isEqualTo("3.80");
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("medium")))
                .isEqualTo("3.80");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenHighGranularity() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("high")))
                .isEqualTo("3.87");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndFirstBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("auto")))
                .isEqualTo("3.85");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndSecondBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(5.32), PriceGranularity.createFromString("auto")))
                .isEqualTo("5.30");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndThirdBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(13.59), PriceGranularity.createFromString("auto")))
                .isEqualTo("13.50");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndFirstBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(2.87), PriceGranularity.createFromString("dense")))
                .isEqualTo("2.87");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndSecondBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(5.36), PriceGranularity.createFromString("dense")))
                .isEqualTo("5.35");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndThirdBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(13.69), PriceGranularity.createFromString("dense")))
                .isEqualTo("13.50");
    }

    @Test
    public void fromCpmShouldReturnResultWithDefaultPrecisionTwoIfBucketPrecisionInNull() {
        assertThat(
                CpmBucket.fromCpm(BigDecimal.valueOf(2.3333), PriceGranularity.createFromBuckets(
                        singletonList(ExtPriceGranularityBucket.of(null, BigDecimal.valueOf(0), BigDecimal.valueOf(3),
                                BigDecimal.valueOf(0.01)))))).isEqualTo("2.33");
    }

    @Test
    public void fromCpmShouldReturnResultWithPrecisionZero() {
        assertThat(
                CpmBucket.fromCpm(BigDecimal.valueOf(2.3333), PriceGranularity.createFromBuckets(
                        singletonList(ExtPriceGranularityBucket.of(0, BigDecimal.valueOf(0), BigDecimal.valueOf(3),
                                BigDecimal.valueOf(0.01)))))).isEqualTo("2");
    }

    @Test
    public void fromCpmAsNumberShouldReturnExpectedResult() {
        // given
        final PriceGranularity priceGranularity = PriceGranularity.createFromBuckets(
                singletonList(ExtPriceGranularityBucket.of(null, BigDecimal.valueOf(0), BigDecimal.valueOf(3),
                        BigDecimal.valueOf(0.01))));

        // when
        final BigDecimal result = CpmBucket.fromCpmAsNumber(BigDecimal.valueOf(2.333), priceGranularity);

        // then
        assertThat(result.compareTo(BigDecimal.valueOf(2.33))).isEqualTo(0);
    }
}

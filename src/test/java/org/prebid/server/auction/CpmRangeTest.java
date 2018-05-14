package org.prebid.server.auction;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;

import java.math.BigDecimal;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class CpmRangeTest {

    @Test
    public void fromCpmShouldReturnMaxRangeIfCpmExceedsIt() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(21), PriceGranularity.createFromString("auto")))
                .isEqualTo("20.00");
    }

    @Test
    public void fromCpmShouldReturnPriceWithCorrectPrecision() {
        // given
        final PriceGranularity priceGranularity = PriceGranularity.createFromExtPriceGranularity(ExtPriceGranularity.of(1, singletonList(
                ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(0.1)))));

        // when
        final String cpm = CpmRange.fromCpm(BigDecimal.valueOf(5.1245), priceGranularity);

        // then
        assertThat(cpm).isEqualTo("5.1");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenLowGranularity() {
        Assertions.assertThat(
                CpmRange.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("low")))
                .isEqualTo("3.50");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenMedGranularity() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("med")))
                .isEqualTo("3.80");
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("medium")))
                .isEqualTo("3.80");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenHighGranularity() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("high")))
                .isEqualTo("3.87");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndFirstRange() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.87), PriceGranularity.createFromString("auto")))
                .isEqualTo("3.85");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndSecondRange() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.32), PriceGranularity.createFromString("auto")))
                .isEqualTo("5.30");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndThirdRange() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.59), PriceGranularity.createFromString("auto")))
                .isEqualTo("13.50");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndFirstRange() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.87), PriceGranularity.createFromString("dense")))
                .isEqualTo("2.87");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndSecondRange() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.36), PriceGranularity.createFromString("dense")))
                .isEqualTo("5.35");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndThirdRange() {
        Assertions.assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.69), PriceGranularity.createFromString("dense")))
                .isEqualTo("13.50");
    }

    @Test
    public void fromCpmShouldReturnResultWithDefaultPrecisionTwoIfRangePrecisionInNull() {
        Assertions.assertThat(
                CpmRange.fromCpm(BigDecimal.valueOf(2.3333), PriceGranularity.createFromExtPriceGranularity(
                        ExtPriceGranularity.of(null, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3),
                                BigDecimal.valueOf(0.01)))))))
                .isEqualTo("2.33");
    }

    @Test
    public void fromCpmShouldReturnResultWithPrecisionZero() {
        Assertions.assertThat(
                CpmRange.fromCpm(BigDecimal.valueOf(2.3333), PriceGranularity.createFromExtPriceGranularity(
                        ExtPriceGranularity.of(0, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3),
                                BigDecimal.valueOf(0.01)))))))
                .isEqualTo("2");
    }

    @Test
    public void fromCpmAsNumberShouldReturnExpectedResult() {
        // given
        final PriceGranularity priceGranularity = PriceGranularity.createFromExtPriceGranularity(ExtPriceGranularity.of(null,
                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)))));

        // when
        final BigDecimal result = CpmRange.fromCpmAsNumber(BigDecimal.valueOf(2.333), priceGranularity);

        // then
        assertThat(result.compareTo(BigDecimal.valueOf(2.33))).isEqualTo(0);
    }

    @Test
    public void fromCpmAsNumberShouldRetunNullIfPriceDoesNotFitToRange() {
        // given
        final PriceGranularity priceGranularity = PriceGranularity.createFromExtPriceGranularity(
                ExtPriceGranularity.of(null, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3),
                        BigDecimal.valueOf(0.01)))));

        // when
        final BigDecimal result = CpmRange.fromCpmAsNumber(BigDecimal.valueOf(-2.0), priceGranularity);

        // then
        assertThat(result).isNull();
    }
}

package org.prebid.server.auction;

import org.junit.Test;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;

import java.math.BigDecimal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class PriceGranularityTest {

    @Test
    public void createFromExtPriceGranularityShouldThrowIllegalArgumentsExceptionIfRangesListNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> PriceGranularity.createFromExtPriceGranularity(
                ExtPriceGranularity.of(2, null)));
    }

    @Test
    public void createFromExtPriceGranularityShouldThrowIllegalArgumentsExceptionIfRangesListIsEmpty() {
        assertThatIllegalArgumentException().isThrownBy(() -> PriceGranularity.createFromExtPriceGranularity(
                ExtPriceGranularity.of(2, emptyList())));
    }

    @Test
    public void createFromStringShouldThrowPrebidExceptionIfInvalidStringType() {
        assertThatExceptionOfType(PreBidException.class).isThrownBy(() -> PriceGranularity.createFromString("invalid"));
    }

    @Test
    public void createCustomPriceGranularityByStringLow() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("low");

        // then
        assertThat(priceGranularity.getRangesMax()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(priceGranularity.getPrecision()).isEqualTo(2);
        assertThat(priceGranularity.getRanges()).containsOnly(
                ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)));
    }

    @Test
    public void createCustomPriceGranularityByStringMedAndMedium() {
        // given and when
        final PriceGranularity priceGranularityMed = PriceGranularity.createFromString("med");
        final PriceGranularity priceGranularityMedium = PriceGranularity.createFromString("medium");

        // then
        assertThat(priceGranularityMed.getRangesMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularityMedium.getRangesMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularityMed.getPrecision()).isEqualTo(2);
        assertThat(priceGranularityMedium.getPrecision()).isEqualTo(2);
        assertThat(priceGranularityMed.getRanges()).containsOnly(
                ExtGranularityRange.of(BigDecimal.valueOf(20), BigDecimal.valueOf(0.1)));
        assertThat(priceGranularityMedium.getRanges()).containsOnly(
                ExtGranularityRange.of(BigDecimal.valueOf(20), BigDecimal.valueOf(0.1)));
    }

    @Test
    public void createCustomPriceGranularityByStringHigh() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("high");

        // then
        assertThat(priceGranularity.getRangesMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularity.getPrecision()).isEqualTo(2);
        assertThat(priceGranularity.getRanges()).containsOnly(
                ExtGranularityRange.of(BigDecimal.valueOf(20), BigDecimal.valueOf(0.01)));
    }

    @Test
    public void createCustomPriceGranularityByStringAuto() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("auto");

        // then
        assertThat(priceGranularity.getRangesMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularity.getPrecision()).isEqualTo(2);
        assertThat(priceGranularity.getRanges()).containsExactly(
                ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.05)),
                ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(0.1)),
                ExtGranularityRange.of(BigDecimal.valueOf(20), BigDecimal.valueOf(0.5))
        );
    }

    @Test
    public void createCustomPriceGranularityByStringDense() {
        // given and when
        final PriceGranularity priceGranularity = PriceGranularity.createFromString("dense");

        // then
        assertThat(priceGranularity.getRangesMax()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(priceGranularity.getRanges()).containsExactly(
                ExtGranularityRange.of(BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtGranularityRange.of(BigDecimal.valueOf(8), BigDecimal.valueOf(0.05)),
                ExtGranularityRange.of(BigDecimal.valueOf(20), BigDecimal.valueOf(0.5)));
    }

    @Test
    public void createFromExtPriceGranularityShouldReturnCorrectPriceGranularity() {
        // given
        final ExtPriceGranularity extPriceGranularity = ExtPriceGranularity.of(2, asList(
                ExtGranularityRange.of(BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtGranularityRange.of(BigDecimal.valueOf(8), BigDecimal.valueOf(0.05))));

        // when
        final PriceGranularity priceGranularity = PriceGranularity.createFromExtPriceGranularity(extPriceGranularity);

        // then
        assertThat(priceGranularity.getRangesMax()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(priceGranularity.getRanges()).containsExactly(
                ExtGranularityRange.of(BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)),
                ExtGranularityRange.of(BigDecimal.valueOf(8), BigDecimal.valueOf(0.05)));
    }
}

package org.prebid.server.auction;

import org.junit.jupiter.api.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionBidRoundingMode;
import org.prebid.server.settings.model.AccountAuctionConfig;

import java.math.BigDecimal;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.auction.PriceGranularity.createFromExtPriceGranularity;
import static org.prebid.server.auction.PriceGranularity.createFromString;
import static org.prebid.server.settings.model.AccountAuctionBidRoundingMode.DOWN;
import static org.prebid.server.settings.model.AccountAuctionBidRoundingMode.TIMESPLIT;
import static org.prebid.server.settings.model.AccountAuctionBidRoundingMode.TRUE;
import static org.prebid.server.settings.model.AccountAuctionBidRoundingMode.UP;

public class CpmRangeTest {

    @Test
    public void fromCpmShouldReturnMaxRangeIfCpmExceedsIt() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(21), createFromString("auto"), givenAccount()))
                .isEqualTo("20.00");
    }

    @Test
    public void fromCpmShouldReturnPriceWithCorrectPrecision() {
        // given
        final PriceGranularity priceGranularity = PriceGranularity.createFromExtPriceGranularity(
                ExtPriceGranularity.of(1, singletonList(
                        ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(0.1)))));

        // when
        final String cpm = CpmRange.fromCpm(BigDecimal.valueOf(5.1245), priceGranularity, givenAccount());

        // then
        assertThat(cpm).isEqualTo("5.1");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenLowGranularity() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.75), createFromString("low"), givenAccount()))
                .isEqualTo("3.50");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.75), createFromString("low"), givenAccount(DOWN)))
                .isEqualTo("3.50");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.75), createFromString("low"), givenAccount(UP)))
                .isEqualTo("4.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.74), createFromString("low"), givenAccount(UP)))
                .isEqualTo("4.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.75), createFromString("low"), givenAccount(TRUE)))
                .isEqualTo("4.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.74), createFromString("low"), givenAccount(TRUE)))
                .isEqualTo("3.50");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenMedGranularity() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("med"), givenAccount()))
                .isEqualTo("3.80");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("med"), givenAccount(DOWN)))
                .isEqualTo("3.80");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("med"), givenAccount(UP)))
                .isEqualTo("3.90");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.84), createFromString("med"), givenAccount(UP)))
                .isEqualTo("3.90");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("med"), givenAccount(TRUE)))
                .isEqualTo("3.90");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.84), createFromString("med"), givenAccount(TRUE)))
                .isEqualTo("3.80");

        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("medium"), givenAccount()))
                .isEqualTo("3.80");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("medium"), givenAccount(DOWN)))
                .isEqualTo("3.80");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("medium"), givenAccount(UP)))
                .isEqualTo("3.90");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.84), createFromString("medium"), givenAccount(UP)))
                .isEqualTo("3.90");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("medium"), givenAccount(TRUE)))
                .isEqualTo("3.90");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.84), createFromString("medium"), givenAccount(TRUE)))
                .isEqualTo("3.80");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenHighGranularity() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("high"), givenAccount()))
                .isEqualTo("3.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("high"), givenAccount(DOWN)))
                .isEqualTo("3.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("high"), givenAccount(UP)))
                .isEqualTo("3.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.84), createFromString("high"), givenAccount(UP)))
                .isEqualTo("3.84");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("high"), givenAccount(TRUE)))
                .isEqualTo("3.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.84), createFromString("high"), givenAccount(TRUE)))
                .isEqualTo("3.84");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.85), createFromString("high"), givenAccount(TIMESPLIT)))
                .isEqualTo("3.85");

    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndFirstRange() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.33), createFromString("dense"), givenAccount()))
                .isEqualTo("3.30");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.33), createFromString("dense"), givenAccount(DOWN)))
                .isEqualTo("3.30");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.33), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("3.35");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.32), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("3.35");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.33), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("3.35");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(3.32), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("3.30");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndSecondRange() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.35), createFromString("auto"), givenAccount()))
                .isEqualTo("5.30");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.35), createFromString("auto"), givenAccount(DOWN)))
                .isEqualTo("5.30");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.35), createFromString("auto"), givenAccount(UP)))
                .isEqualTo("5.40");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.34), createFromString("auto"), givenAccount(UP)))
                .isEqualTo("5.40");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.35), createFromString("auto"), givenAccount(TRUE)))
                .isEqualTo("5.40");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.34), createFromString("auto"), givenAccount(TRUE)))
                .isEqualTo("5.30");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenAutoGranularityAndThirdRange() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("auto"), givenAccount()))
                .isEqualTo("13.50");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("auto"), givenAccount(DOWN)))
                .isEqualTo("13.50");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("auto"), givenAccount(UP)))
                .isEqualTo("14.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.74), createFromString("auto"), givenAccount(UP)))
                .isEqualTo("14.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("auto"), givenAccount(TRUE)))
                .isEqualTo("14.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.74), createFromString("auto"), givenAccount(TRUE)))
                .isEqualTo("13.50");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndFirstRange() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.85), createFromString("dense"), givenAccount()))
                .isEqualTo("2.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.85), createFromString("dense"), givenAccount(DOWN)))
                .isEqualTo("2.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.85), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("2.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.84), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("2.84");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.85), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("2.85");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.84), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("2.84");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.85), createFromString("dense"), givenAccount(TIMESPLIT)))
                .isEqualTo("2.85");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndSecondRange() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.33), createFromString("dense"), givenAccount()))
                .isEqualTo("5.30");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.33), createFromString("dense"), givenAccount(DOWN)))
                .isEqualTo("5.30");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.33), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("5.35");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.32), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("5.35");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.33), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("5.35");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(5.32), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("5.30");
    }

    @Test
    public void fromCpmShouldReturnCpmGivenDenseGranularityAndThirdRange() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("dense"), givenAccount()))
                .isEqualTo("13.50");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("dense"), givenAccount(DOWN)))
                .isEqualTo("13.50");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("14.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.74), createFromString("dense"), givenAccount(UP)))
                .isEqualTo("14.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.75), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("14.00");
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(13.74), createFromString("dense"), givenAccount(TRUE)))
                .isEqualTo("13.50");
    }

    @Test
    public void fromCpmShouldReturnResultWithDefaultPrecisionTwoIfRangePrecisionInNull() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.3333), createFromExtPriceGranularity(
                                ExtPriceGranularity.of(null, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3),
                                        BigDecimal.valueOf(0.01))))), givenAccount()))
                .isEqualTo("2.33");
    }

    @Test
    public void fromCpmShouldReturnResultWithPrecisionZero() {
        assertThat(CpmRange.fromCpm(BigDecimal.valueOf(2.3333), createFromExtPriceGranularity(
                                ExtPriceGranularity.of(0, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3),
                                        BigDecimal.valueOf(0.01))))), givenAccount()))
                .isEqualTo("2");
    }

    @Test
    public void fromCpmAsNumberShouldReturnExpectedResult() {
        // given
        final PriceGranularity priceGranularity = createFromExtPriceGranularity(
                ExtPriceGranularity.of(null,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3), BigDecimal.valueOf(0.01)))));

        // when
        final BigDecimal result = CpmRange.fromCpmAsNumber(BigDecimal.valueOf(2.333), priceGranularity, givenAccount());

        // then
        assertThat(result.compareTo(BigDecimal.valueOf(2.33))).isEqualTo(0);
    }

    @Test
    public void fromCpmAsNumberShouldReturnExpectedResultForMultipleRanges() {
        // given
        final PriceGranularity priceGranularity = createFromExtPriceGranularity(
                ExtPriceGranularity.of(2, asList(
                        ExtGranularityRange.of(BigDecimal.valueOf(1.5), BigDecimal.ONE),
                        ExtGranularityRange.of(BigDecimal.valueOf(2.5), BigDecimal.valueOf(1.2)))));

        // when
        final BigDecimal result = CpmRange.fromCpmAsNumber(BigDecimal.valueOf(2), priceGranularity, givenAccount());

        // then
        assertThat(result.compareTo(BigDecimal.valueOf(1.5))).isEqualTo(0);
    }

    @Test
    public void fromCpmAsNumberShouldRetunNullIfPriceDoesNotFitToRange() {
        // given
        final PriceGranularity priceGranularity = createFromExtPriceGranularity(
                ExtPriceGranularity.of(null, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(3),
                        BigDecimal.valueOf(0.01)))));

        // when
        final BigDecimal result = CpmRange.fromCpmAsNumber(BigDecimal.valueOf(-2.0), priceGranularity, givenAccount());

        // then
        assertThat(result).isNull();
    }

    private static Account givenAccount(AccountAuctionBidRoundingMode mode) {
        return Account.builder().auction(AccountAuctionConfig.builder().bidRounding(mode).build()).build();
    }

    private static Account givenAccount() {
        return Account.builder().build();
    }
}

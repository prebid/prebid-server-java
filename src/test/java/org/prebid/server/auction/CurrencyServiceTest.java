package org.prebid.server.auction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.Currency;
import org.prebid.server.exception.PreBidException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class CurrencyServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LatestRatesService latestRatesService;

    private CurrencyService currencyService;

    @Before
    public void setUp() {
        currencyService = new CurrencyService(latestRatesService);
    }

    @Test
    public void convertCurrencyShouldReturnSamePriceIfBidAndServerCurrenciesEquals() {
        // given
        final BigDecimal price = BigDecimal.valueOf(100);

        // when
        final BigDecimal convertedPrice = currencyService.convertCurrency(price,
                Currency.of(null, null), "USD", "USD");

        // then
        assertThat(convertedPrice).isSameAs(price);
    }

    @Test
    public void convertCurrencyShouldUseUSDByDefaultIfBidCurrencyIsNull() {
        // given
        final Currency requestCurrency = Currency.of(null, singletonMap("GBP",
                singletonMap("USD", BigDecimal.valueOf(1.4306))));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestCurrency, "GBP", null);

        // then
        System.out.println(price);
        assertThat(price.compareTo(BigDecimal.valueOf(0.699))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByStraightMultiplierPrice() {
        // given
        final Currency requestCurrency = Currency.of(null, singletonMap("GBP",
                singletonMap("EUR", BigDecimal.valueOf(1.1565))));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestCurrency, "GBP", "EUR");

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(0.86467))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByInvertedMultiplierPrice() {
        // given
        final Currency requestCurrency = Currency.of(null, singletonMap("GBP",
                singletonMap("EUR", BigDecimal.valueOf(1.1565))));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestCurrency, "EUR", "GBP");

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(1.15651))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByIntermediateMultiplierPrice() {
        // given
        final Map<String, Map<String, BigDecimal>> rates = new HashMap<>();
        rates.put("GBP", singletonMap("USD", BigDecimal.valueOf(1.4306)));
        rates.put("EUR", singletonMap("USD", BigDecimal.valueOf(1.2304)));
        final Currency requestCurrency = Currency.of(null, rates);

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestCurrency, "EUR", "GBP");

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(1.16272))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldUseLatestRatesIfRequestRatesIsNull() {
        // given
        final Map<String, Map<String, BigDecimal>> latestRates = singletonMap("GBP",
                singletonMap("EUR", BigDecimal.valueOf(1.1565)));

        given(latestRatesService.getRates()).willReturn(latestRates);

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, null, "EUR", "GBP");

        // then
        verify(latestRatesService).getRates();
        assertThat(price.compareTo(BigDecimal.valueOf(1.15651))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldUseLatestRatesIfMultiplierWasNotFoundInRequestRates() {
        // given
        final Currency requestCurrency = Currency.of(null, singletonMap("USD",
                singletonMap("EUR", BigDecimal.valueOf(0.8434))));

        final Map<String, Map<String, BigDecimal>> latestRates = singletonMap("GBP",
                singletonMap("EUR", BigDecimal.valueOf(1.1565)));

        given(latestRatesService.getRates()).willReturn(latestRates);

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestCurrency, "EUR", "GBP");

        // then
        verify(latestRatesService).getRates();
        assertThat(price.compareTo(BigDecimal.valueOf(1.15651))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldThrowPrebidExceptionIfServerAndRequestRatesAreNull() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, null, "USD", "EUR"))
                .withMessage("no currency conversion available");
    }

    @Test
    public void convertCurrencyShouldThrowPrebidExceptionIfMultiplierWasNotFoundFromBothRates() {
        // given
        final Currency requestCurrency = Currency.of(null, singletonMap("USD",
                singletonMap("EUR", BigDecimal.valueOf(0.8434))));

        final Map<String, Map<String, BigDecimal>> latestRates = singletonMap("GBP",
                singletonMap("EUR", BigDecimal.valueOf(1.1565)));

        given(latestRatesService.getRates()).willReturn(latestRates);

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, requestCurrency, "EUR", "AUD"))
                .withMessage("no currency conversion available");
    }
}

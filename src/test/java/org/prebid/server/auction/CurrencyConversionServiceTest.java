package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.currency.proto.CurrencyConversionRates;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CurrencyConversionServiceTest extends VertxTest {

    private static final String USD = "USD";
    private static final String GBP = "GBP";
    private static final String EUR = "EUR";
    private static final String UAH = "UAH";
    private static final String AUD = "AUD";
    private static final String URL = "http://currency-rates/latest.json";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private Vertx vertx;

    private CurrencyConversionService currencyService;

    @Before
    public void setUp() throws JsonProcessingException {
        final Map<String, Map<String, BigDecimal>> currencyRates = new HashMap<>();
        currencyRates.put(GBP, singletonMap(EUR, BigDecimal.valueOf(1.15)));
        currencyRates.put(UAH, singletonMap(EUR, BigDecimal.valueOf(1.1565)));
        givenHttpClientReturnsResponse(httpClient, 200,
                mapper.writeValueAsString(CurrencyConversionRates.of(null, currencyRates)));

        currencyService = createAndInitService(URL, 1L, vertx, httpClient);
    }

    @Test
    public void creationShouldFailOnInvalidCurrencyServerUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createAndInitService("invalid-url", 1L, vertx, httpClient))
                .withMessage("URL supplied is not valid: invalid-url");
    }

    @Test
    public void creationShouldFailOnInvalidPeriodValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createAndInitService(URL, 0L, vertx, httpClient))
                .withMessage("Refresh period for updating rates must be positive value");
    }

    @Test
    public void initializeShouldSetLastUpdatedDate() {
        assertThat(currencyService.getLastUpdated()).isNotNull();
    }

    @Test
    public void convertCurrencyShouldReturnSamePriceIfBidAndServerCurrenciesEquals() {
        // given
        final BigDecimal price = BigDecimal.valueOf(100);

        // when
        final BigDecimal convertedPrice = currencyService.convertCurrency(price, null, USD, USD);

        // then
        assertThat(convertedPrice).isSameAs(price);
    }

    @Test
    public void convertCurrencyShouldUseUSDByDefaultIfBidCurrencyIsNull() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates =
                singletonMap(GBP, singletonMap(USD, BigDecimal.valueOf(1.4306)));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, GBP, null);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(0.699))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByStraightMultiplierPrice() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = singletonMap(GBP,
                singletonMap(EUR, BigDecimal.valueOf(1.1565)));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, GBP, EUR);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(0.865))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByInvertedMultiplierPrice() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = singletonMap(GBP, singletonMap(EUR,
                BigDecimal.valueOf(1.1565)));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, EUR, GBP);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(1.156))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByIntermediateMultiplierPrice() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = new HashMap<>();
        requestConversionRates.put(GBP, singletonMap(USD, BigDecimal.valueOf(1.4306)));
        requestConversionRates.put(EUR, singletonMap(USD, BigDecimal.valueOf(1.2304)));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, EUR, GBP);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(1.163))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedBySingleDigitMultiplierPrice() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = new HashMap<>();
        requestConversionRates.put(EUR, singletonMap(USD, BigDecimal.valueOf(0.5)));

        // when
        final BigDecimal price = currencyService.convertCurrency(new BigDecimal("1.23"), requestConversionRates, EUR, USD);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(2.460))).isEqualTo(0);
    }


    @Test
    public void convertCurrencyShouldUseLatestRatesIfRequestRatesIsNull() {
        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, null, EUR, GBP);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(1.149))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldUseLatestRatesIfMultiplierWasNotFoundInRequestRates() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = singletonMap(USD,
                singletonMap(EUR, BigDecimal.valueOf(0.8434)));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, EUR, UAH);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(1.156))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnSamePriceIfBidCurrencyIsNullAndServerCurrencyUSD() {
        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, emptyMap(), USD, null);

        // then
        assertThat(price.compareTo(BigDecimal.ONE)).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldThrowPrebidExceptionIfServerAndRequestRatesAreNull() {
        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, null, USD, EUR))
                .withMessage("no currency conversion available");
    }

    @Test
    public void convertCurrencyShouldThrowPrebidExceptionIfMultiplierWasNotFoundFromBothRates() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = singletonMap(USD,
                singletonMap(EUR, BigDecimal.valueOf(0.8434)));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, EUR, AUD))
                .withMessage("no currency conversion available");
    }

    @Test
    public void convertCurrencyShouldThrowExceptionWhenCurrencyServerResponseStatusNot200() {
        // given
        givenHttpClientReturnsResponse(httpClient, 503, "server unavailable");

        // when
        currencyService = createAndInitService(URL, 1L, vertx, httpClient);

        // then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, null, UAH, AUD))
                .withMessage("no currency conversion available");
    }

    @Test
    public void convertCurrencyShouldThrowExceptionWhenCurrencyServerResponseContainsMalformedBody() {
        // given
        givenHttpClientReturnsResponse(httpClient, 200, "{\"foo\": \"bar\"}");

        // when
        currencyService = createAndInitService(URL, 1L, vertx, httpClient);

        // then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, null, UAH, AUD))
                .withMessage("no currency conversion available");
    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduled() {
        // given
        final Vertx vertx = Vertx.vertx();
        final HttpClient httpClient = mock(HttpClient.class);
        givenHttpClientReturnsResponse(httpClient, 200, "{\"foo\": \"bar\"}");

        // when
        currencyService = createAndInitService(URL, 1000, vertx, httpClient);

        // then
        verify(httpClient, after(2100).times(3)).get(anyString(), anyLong());
        vertx.close();
    }

    private static CurrencyConversionService createAndInitService(String url, long refreshPeriod, Vertx vertx,
                                                                  HttpClient httpClient) {
        final CurrencyConversionService currencyService =
                new CurrencyConversionService(url, refreshPeriod, vertx, httpClient);
        currencyService.initialize();
        return currencyService;
    }

    private static void givenHttpClientReturnsResponse(HttpClient httpClient, int statusCode, String response) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(statusCode, null, response);
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }
}

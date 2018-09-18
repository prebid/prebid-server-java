package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.currency.proto.CurrencyConversionRates;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.http.HttpClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class CurrencyConversionServiceTest extends VertxTest {

    private static final String USD = "USD";
    private static final String GBP = "GBP";
    private static final String EUR = "EUR";
    private static final String UAH = "UAH";
    private static final String AUD = "AUD";
    private static final String URL = "http://currency.prebid.org/latest.json";

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
        assertThat(price.compareTo(BigDecimal.valueOf(0.69901))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByStraightMultiplierPrice() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = singletonMap(GBP,
                singletonMap(EUR, BigDecimal.valueOf(1.1565)));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, GBP, EUR);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(0.86468))).isEqualTo(0);
    }

    @Test
    public void convertCurrencyShouldReturnConvertedByInvertedMultiplierPrice() {
        // given
        final Map<String, Map<String, BigDecimal>> requestConversionRates = singletonMap(GBP, singletonMap(EUR,
                BigDecimal.valueOf(1.1565)));

        // when
        final BigDecimal price = currencyService.convertCurrency(BigDecimal.ONE, requestConversionRates, EUR, GBP);

        // then
        assertThat(price.compareTo(BigDecimal.valueOf(1.1565))).isEqualTo(0);
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
        assertThat(price.compareTo(BigDecimal.valueOf(1.16271))).isEqualTo(0);
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
        assertThat(price.compareTo(BigDecimal.valueOf(1.1565))).isEqualTo(0);
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
        final HttpClient client = mock(HttpClient.class);
        givenHttpClientReturnsResponse(client, 503, "server unavailable");

        // when
        currencyService = createAndInitService(URL, 1L, vertx, client);

        // then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, null, UAH, AUD))
                .withMessage("no currency conversion available");
    }

    @Test
    public void convertCurrencyShouldThrowExceptionWhenCurrencyServerResponseContainsMalformedBody() {
        // given
        final HttpClient client = mock(HttpClient.class);
        givenHttpClientReturnsResponse(client, 200, "{\"foo\": \"bar\"}");

        // when
        currencyService = createAndInitService(URL, 1L, vertx, client);

        // then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> currencyService.convertCurrency(BigDecimal.ONE, null, UAH, AUD))
                .withMessage("no currency conversion available");
    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduled() {
        // given
        final HttpClient client = mock(HttpClient.class);
        final Vertx vertx = Vertx.vertx();

        // when
        currencyService = createAndInitService(URL, 1000, vertx, client);

        // then
        verify(client, after(2100).times(3))
                .request(any(), anyString(), any(), any(), anyLong(), any(), any());
        vertx.close();
    }

    private void givenHttpClientReturnsResponse(HttpClient httpClient, int statusCode, String body) {

        final HttpClientResponse httpClientResponse = givenHttpClientResponse(httpClient, statusCode);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(body), 0));
    }

    private HttpClientResponse givenHttpClientResponse(HttpClient httpClient, int statusCode) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(statusCode);

        doAnswer(withSelfAndPassObjectToHandler(httpClientResponse, 5))
                .when(httpClient).request(any(), anyString(), any(), any(), anyLong(), any(), any());

        return httpClientResponse;
    }

    private CurrencyConversionService createAndInitService(String url, long refreshPeriod, Vertx vertx,
                                                           HttpClient httpClient) {
        final CurrencyConversionService currencyService =
                new CurrencyConversionService(url, refreshPeriod, vertx, httpClient);
        currencyService.initialize();
        return currencyService;
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj, int position) {
        return inv -> {
            ((Handler<T>) inv.getArgument(position)).handle(obj);
            return inv.getMock();
        };
    }
}

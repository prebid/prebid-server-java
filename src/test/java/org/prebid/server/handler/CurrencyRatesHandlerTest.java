package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.util.AsciiString;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.handler.admin.CurrencyRatesHandler;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class CurrencyRatesHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private CurrencyConversionService currencyConversionService;

    private CurrencyRatesHandler currencyRatesHandler;

    @Before
    public void setUp() {
        currencyRatesHandler = new CurrencyRatesHandler(currencyConversionService, "/endpoint", jacksonMapper);

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(), any(AsciiString.class))).willReturn(httpResponse);
    }

    @Test
    public void handleShouldReturnLastUpdatedString() throws JsonProcessingException {
        // given
        given(currencyConversionService.isExternalRatesActive())
                .willReturn(true);
        given(currencyConversionService.getCurrencyServerUrl())
                .willReturn("http://currency-server/latest");
        given(currencyConversionService.getRefreshPeriod())
                .willReturn(12345L);
        given(currencyConversionService.getLastUpdated())
                .willReturn(ZonedDateTime.parse("2018-11-06T19:25:48.085Z"));

        final Map<String, Map<String, BigDecimal>> rates = new HashMap<>();
        rates.put("USD", singletonMap("EUR", BigDecimal.TEN));
        rates.put("EUR", singletonMap("USD", BigDecimal.ONE));
        given(currencyConversionService.getExternalCurrencyRates())
                .willReturn(rates);

        // when
        currencyRatesHandler.handle(routingContext);

        // then
        final String expectedResponse = mapper.writeValueAsString(
                mapper.createObjectNode()
                        .put("active", true)
                        .put("source", "http://currency-server/latest")
                        .put("fetchingIntervalNs", 12345000000L)
                        .put("lastUpdated", "2018-11-06T19:25:48.085Z")
                        .<ObjectNode>set("rates", mapper.createObjectNode()
                                .<ObjectNode>set("EUR", mapper.createObjectNode()
                                        .put("USD", BigDecimal.ONE))
                                .<ObjectNode>set("USD", mapper.createObjectNode()
                                        .put("EUR", BigDecimal.TEN))));
        verify(httpResponse).end(eq(expectedResponse));
    }
}

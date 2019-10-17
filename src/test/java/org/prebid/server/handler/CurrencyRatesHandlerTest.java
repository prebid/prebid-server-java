package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.currency.CurrencyConversionService;

import java.time.ZonedDateTime;

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
        currencyRatesHandler = new CurrencyRatesHandler(currencyConversionService);

        given(routingContext.response()).willReturn(httpResponse);
    }

    @Test
    public void handleShouldReturnLastUpdatedString() throws JsonProcessingException {
        // given
        given(currencyConversionService.getLastUpdated())
                .willReturn(ZonedDateTime.parse("2018-11-06T19:25:48.085Z"));

        // when
        currencyRatesHandler.handle(routingContext);

        // then
        final String expectedResponse = mapper.writeValueAsString(Response.of("2018-11-06T19:25:48.085Z"));
        verify(httpResponse).end(expectedResponse);
    }

    @Test
    public void handleShouldRespondWithNoValue() throws JsonProcessingException {
        // given
        given(currencyConversionService.getLastUpdated()).willReturn(null);

        // when
        currencyRatesHandler.handle(routingContext);

        // then
        verify(httpResponse).end(mapper.writeValueAsString(Response.of("no value")));
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class Response {

        String lastUpdate;
    }
}
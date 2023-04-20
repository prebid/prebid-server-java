package org.prebid.server.handler;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.handler.admin.HttpInteractionLogHandler;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.model.HttpLogSpec;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class HttpInteractionLogHandlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpInteractionLogger httpInteractionLogger;

    private HttpInteractionLogHandler handler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        handler = new HttpInteractionLogHandler(5, httpInteractionLogger, "/endpoint");
    }

    @Test
    public void shouldSetSpecWhenAllParametersPresent() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("endpoint", "auction")
                .add("statusCode", "400")
                .add("account", "123")
                .add("bidder", "ix")
                .add("limit", "2"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpInteractionLogger).setSpec(HttpLogSpec.of(HttpLogSpec.Endpoint.auction, 400, "123", "ix", 2));
    }

    @Test
    public void shouldSetSpecWhenOptionalParametersAbsent() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("limit", "2"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpInteractionLogger).setSpec(eq(HttpLogSpec.of(null, null, null, null, 2)));
    }

    @Test
    public void shouldRespondWithErrorWhenEndpointNotValid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("endpoint", "abc")
                .add("limit", "2"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid 'endpoint' parameter value, allowed values '[auction, amp]'"));

        verifyNoInteractions(httpInteractionLogger);
    }

    @Test
    public void shouldRespondWithErrorWhenStatusCodeNotInteger() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("statusCode", "abc")
                .add("limit", "2"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid 'statusCode' parameter value"));

        verifyNoInteractions(httpInteractionLogger);
    }

    @Test
    public void shouldRespondWithErrorWhenStatusCodeNotValid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("statusCode", "1000")
                .add("limit", "2"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Parameter 'statusCode' must be between 200 and 500"));

        verifyNoInteractions(httpInteractionLogger);
    }

    @Test
    public void shouldRespondWithErrorWhenLimitAbsent() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Missing required parameter 'limit'"));

        verifyNoInteractions(httpInteractionLogger);
    }

    @Test
    public void shouldRespondWithErrorWhenLimitNotInteger() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("limit", "abc"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid 'limit' parameter value"));

        verifyNoInteractions(httpInteractionLogger);
    }

    @Test
    public void shouldRespondWithErrorWhenLimitNotValid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("limit", "10"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Parameter 'limit' must be between 0 and 5"));

        verifyNoInteractions(httpInteractionLogger);
    }
}

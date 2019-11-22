package org.prebid.server.handler;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.execution.LogModifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AdminHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LogModifier logModifier;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    private AdminHandler adminHandler;

    @Before
    public void setUp() {
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.request()).willReturn(httpRequest);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        adminHandler = new AdminHandler(logModifier);
    }

    @Test
    public void shouldRespondWithErrorWhenLoggingParamIsMissing() {
        // given
        given(httpRequest.getParam(eq("logging"))).willReturn(null);

        // when
        adminHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("Invalid LoggingLevel: null"));
        verify(httpResponse).setStatusCode(eq(400));
        verifyZeroInteractions(logModifier);
    }

    @Test
    public void shouldRespondWithErrorWhenLoggingIsInvalid() {
        // given
        given(httpRequest.getParam(eq("logging"))).willReturn("spam");

        // when
        adminHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("Invalid LoggingLevel: spam"));
        verify(httpResponse).setStatusCode(eq(400));
        verifyZeroInteractions(logModifier);
    }

    @Test
    public void shouldRespondWithErrorWhenRecordsIsMissing() {
        // given
        given(httpRequest.getParam(eq("logging"))).willReturn("error");
        given(httpRequest.getParam(eq("records"))).willReturn(null);

        // when
        adminHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("Invalid records parameter: null"));
        verify(httpResponse).setStatusCode(eq(400));
        verifyZeroInteractions(logModifier);
    }

    @Test
    public void shouldRespondWithErrorWhenRecordsIsInvalid() {
        // given
        given(httpRequest.getParam(eq("logging"))).willReturn("error");
        given(httpRequest.getParam(eq("records"))).willReturn("spam");

        // when
        adminHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("Invalid records parameter: spam"));
        verify(httpResponse).setStatusCode(eq(400));
        verifyZeroInteractions(logModifier);
    }

    @Test
    public void shouldRespondWithErrorWhenRecordsIsNegative() {
        // given
        given(httpRequest.getParam(eq("logging"))).willReturn("error");
        given(httpRequest.getParam(eq("records"))).willReturn("-123");

        // when
        adminHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("Invalid records parameter: -123"));
        verify(httpResponse).setStatusCode(eq(400));
        verifyZeroInteractions(logModifier);
    }

    @Test
    public void shouldRespondWithOkAndSetErrorOnBadRequestCount() {
        // given
        given(httpRequest.getParam(eq("logging"))).willReturn("error");
        given(httpRequest.getParam(eq("records"))).willReturn("123");

        // when
        adminHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("Logging level was changed to error, for 123 requests"));
        verify(logModifier).set(any(), eq(123));
    }
}


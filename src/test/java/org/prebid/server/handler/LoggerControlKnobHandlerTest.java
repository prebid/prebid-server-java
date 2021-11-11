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
import org.prebid.server.log.LoggerControlKnob;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class LoggerControlKnobHandlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LoggerControlKnob loggerControlKnob;

    private LoggerControlKnobHandler handler;

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

        handler = new LoggerControlKnobHandler(10000L, loggerControlKnob, "endpoint");
    }

    @Test
    public void shouldChangeLevelWhenAllParametersPresent() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("level", "info")
                .add("duration", "1000"));

        // when
        handler.handle(routingContext);

        // then
        verify(loggerControlKnob).changeLogLevel("info", Duration.ofMillis(1000L));
    }

    @Test
    public void shouldRespondWithErrorWhenLevelAbsent() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Missing required parameter 'level'"));

        verifyNoInteractions(loggerControlKnob);
    }

    @Test
    public void shouldRespondWithErrorWhenLevelNotValid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("level", "abc"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid 'level' parameter value, allowed values '[warn, debug, error, info]'"));

        verifyNoInteractions(loggerControlKnob);
    }

    @Test
    public void shouldRespondWithErrorWhenDurationAbsent() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("level", "info"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Missing required parameter 'duration'"));

        verifyNoInteractions(loggerControlKnob);
    }

    @Test
    public void shouldRespondWithErrorWhenDurationNotInteger() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("level", "info")
                .add("duration", "abc"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid 'duration' parameter value"));

        verifyNoInteractions(loggerControlKnob);
    }

    @Test
    public void shouldRespondWithErrorWhenDurationNotValid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("level", "info")
                .add("duration", "20000"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Parameter 'duration' must be between 0 and 10000"));

        verifyNoInteractions(loggerControlKnob);
    }
}

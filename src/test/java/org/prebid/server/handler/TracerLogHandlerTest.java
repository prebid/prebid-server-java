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
import org.prebid.server.VertxTest;
import org.prebid.server.log.CriteriaManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class TracerLogHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CriteriaManager criteriaManager;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private HttpServerRequest httpRequest;

    private TracerLogHandler tracerLogHandler;

    @Before
    public void setUp() {
        tracerLogHandler = new TracerLogHandler(criteriaManager);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.request()).willReturn(httpRequest);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
    }

    @Test
    public void handleShouldReturnBadRequestWhenNoParametersInRequest() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        tracerLogHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("At least one parameter should ne defined: account, bidderCode, lineItemId"));
    }

    @Test
    public void handleShouldReturnBadRequestWhenDurationWasNotDefined() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap().add("account", "1001"));

        // when
        tracerLogHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("duration parameter should be defined"));
    }

    @Test
    public void handleShouldReturnBadRequestWhenDurationHasIncorrectFormat() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap().add("account", "1001")
                .add("duration", "invalid"));

        // when
        tracerLogHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("duration parameter should be defined as integer, but was invalid"));
    }

    @Test
    public void handleShouldReturnBadRequestWhenLogLevelHasIncorrectValue() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap().add("account", "1001")
                .add("duration", "200").add("level", "invalid"));
        doThrow(new IllegalArgumentException("Invalid LoggingLevel: invalid"))
                .when(criteriaManager).addCriteria(any(), any(), any(), any(), any());

        // when
        tracerLogHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid parameter: Invalid LoggingLevel: invalid"));
    }
}

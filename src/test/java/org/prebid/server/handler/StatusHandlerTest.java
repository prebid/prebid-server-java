package org.prebid.server.handler;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

public class StatusHandlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    private StatusHandler statusHandler;

    @Test
    public void shouldRespondHttp200OkWithMessage() {
        // given
        statusHandler = new StatusHandler("Response message");
        given(routingContext.response()).willReturn(httpResponse);

        // when
        statusHandler.handle(routingContext);

        // then
        verify(httpResponse, only()).end(eq("Response message"));
    }

    @Test
    public void shouldRespondWithNoContentWhenMessageWasNotDefined() {
        statusHandler = new StatusHandler(null);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(eq(204))).willReturn(httpResponse);

        // when
        statusHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(204));
    }
}

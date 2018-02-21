package org.rtb.vexing.handler;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NoCacheHandlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerResponse httpResponse;

    @Test
    public void testNoCacheHeadersShouldBeInResponse() {
        // given
        final NoCacheHandler noCacheHandler = new NoCacheHandler();
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), anyString())).willReturn(httpResponse);

        // when
        noCacheHandler.handle(routingContext);

        // then
        verify(httpResponse, times(3)).putHeader(any(CharSequence.class), anyString());
        verify(httpResponse).putHeader(eq(HttpHeaders.CACHE_CONTROL), eq("no-cache, no-store, must-revalidate"));
        verify(httpResponse).putHeader(eq(HttpHeaders.createOptimized("Pragma")), eq("no-cache"));
        verify(httpResponse).putHeader(eq(HttpHeaders.EXPIRES), eq("0"));
        verify(routingContext).next();
    }
}

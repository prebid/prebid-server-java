package org.prebid.server.handler;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.util.HttpUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class NoCacheHandlerTest {

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
        verify(httpResponse).putHeader(eq(HttpUtil.CACHE_CONTROL_HEADER), eq("no-cache, no-store, must-revalidate"));
        verify(httpResponse).putHeader(eq(HttpUtil.PRAGMA_HEADER), eq("no-cache"));
        verify(httpResponse).putHeader(eq(HttpUtil.EXPIRES_HEADER), eq("0"));
        verify(routingContext).next();
    }
}

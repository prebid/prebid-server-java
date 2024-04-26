package org.prebid.server.handler;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.handler.admin.AccountCacheInvalidationHandler;
import org.prebid.server.settings.CachingApplicationSettings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AccountCacheInvalidationHandlerTest extends VertxTest {

    @Mock
    private CachingApplicationSettings cachingApplicationSettings;

    private AccountCacheInvalidationHandler handler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @BeforeEach
    public void setUp() {
        handler = new AccountCacheInvalidationHandler(cachingApplicationSettings, "/endpoint");

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.closed()).willReturn(false);
    }

    @Test
    public void shouldReturnBadRequestWhenAccountParamIsMissing() {
        // given
        given(httpRequest.getParam(any())).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Account id is not defined"));

        verify(httpRequest).getParam(eq("account"));
    }

    @Test
    public void shouldTriggerInvalidateWhenAccountIdIsPresent() {
        // given
        given(httpRequest.getParam(any())).willReturn("123");

        // when
        handler.handle(routingContext);

        // then
        verify(cachingApplicationSettings).invalidateAccountCache("123");

        verify(httpRequest).getParam(eq("account"));
    }
}

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
import org.prebid.server.settings.CachingApplicationSettings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class AccountCacheInvalidationHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CachingApplicationSettings cachingApplicationSettings;

    private AccountCacheInvalidationHandler handler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        handler = new AccountCacheInvalidationHandler(cachingApplicationSettings);

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.response().setStatusCode(anyInt())).willReturn(httpResponse);
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

        verify(httpRequest).getParam("account");
    }

    @Test
    public void shouldReturnOkAndTriggerInvalidateWhenAccountIdIsPresent() {
        // given
        given(httpRequest.getParam(any())).willReturn("123");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(200));
        verify(cachingApplicationSettings).invalidateAccountCache("123");

        verify(httpRequest).getParam("account");
    }
}


package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.proto.request.InvalidateSettingsCacheRequest;
import org.prebid.server.settings.proto.request.UpdateSettingsCacheRequest;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class SettingsCacheNotificationHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CacheNotificationListener cacheNotificationListener;

    private SettingsCacheNotificationHandler handler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        handler = new SettingsCacheNotificationHandler(cacheNotificationListener, jacksonMapper, "endpoint");

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.response().setStatusCode(anyInt())).willReturn(httpResponse);
    }

    @Test
    public void shouldReturnBadRequestForUpdateCacheIfRequestHasNoBody() {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.POST);
        given(routingContext.getBody()).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Missing update data."));
    }

    @Test
    public void shouldReturnBadRequestForUpdateCacheIfRequestBodyCannotBeParsed() {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.POST);
        given(routingContext.getBody()).willReturn(Buffer.buffer());

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid update."));
    }

    @Test
    public void shouldAskListenerToUpdateCache() throws JsonProcessingException {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.POST);

        final UpdateSettingsCacheRequest cacheRequest = UpdateSettingsCacheRequest.of(
                singletonMap("reqId1", "reqValue1"), singletonMap("impId1", "impValue1"));
        given(routingContext.getBody()).willReturn(Buffer.buffer(mapper.writeValueAsBytes(cacheRequest)));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheNotificationListener).save(
                eq(singletonMap("reqId1", "reqValue1")), eq(singletonMap("impId1", "impValue1")));
    }

    @Test
    public void shouldReturnBadRequestForInvalidateCacheIfRequestHasNoBody() {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.DELETE);
        given(routingContext.getBody()).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Missing invalidation data."));
    }

    @Test
    public void shouldReturnBadRequestForInvalidateCacheIfRequestBodyCannotBeParsed() {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.DELETE);
        given(routingContext.getBody()).willReturn(Buffer.buffer());

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid invalidation."));
    }

    @Test
    public void shouldAskListenerToInvalidateCache() throws JsonProcessingException {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.DELETE);

        final InvalidateSettingsCacheRequest cacheRequest = InvalidateSettingsCacheRequest.of(
                singletonList("reqId1"), singletonList("impId1"));
        given(routingContext.getBody()).willReturn(Buffer.buffer(mapper.writeValueAsBytes(cacheRequest)));

        // when
        handler.handle(routingContext);

        // then
        verify(cacheNotificationListener).invalidate(
                eq(singletonList("reqId1")), eq(singletonList("impId1")));
    }

    @Test
    public void shouldReturnMethodNotAllowedStatusResponseIfRequestHasNeitherPostOrDeleteMethod() {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.GET);
        given(routingContext.getBody()).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(405));
        verifyZeroInteractions(cacheNotificationListener);
    }
}

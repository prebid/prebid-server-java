package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.handler.admin.SettingsCacheNotificationHandler;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.proto.request.InvalidateSettingsCacheRequest;
import org.prebid.server.settings.proto.request.UpdateSettingsCacheRequest;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class SettingsCacheNotificationHandlerTest extends VertxTest {

    @Mock
<<<<<<< HEAD
    private CacheNotificationListener<String> cacheNotificationListener;
=======
    private CacheNotificationListener cacheNotificationListener;
>>>>>>> 04d9d4a13 (Initial commit)

    private SettingsCacheNotificationHandler handler;
    @Mock(strictness = LENIENT)
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock(strictness = LENIENT)
    private RequestBody requestBody;

    @BeforeEach
    public void setUp() {
<<<<<<< HEAD
        handler = new SettingsCacheNotificationHandler("endpoint", cacheNotificationListener, jacksonMapper);
=======
        handler = new SettingsCacheNotificationHandler(cacheNotificationListener, jacksonMapper, "endpoint");
>>>>>>> 04d9d4a13 (Initial commit)

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.body()).willReturn(requestBody);
        given(routingContext.response().setStatusCode(anyInt())).willReturn(httpResponse);
    }

    @Test
    public void shouldReturnBadRequestForUpdateCacheIfRequestHasNoBody() {
        // given
        given(routingContext.request().method()).willReturn(HttpMethod.POST);
        given(requestBody.buffer()).willReturn(null);

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
        given(requestBody.buffer()).willReturn(Buffer.buffer());

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
        given(requestBody.buffer()).willReturn(Buffer.buffer(mapper.writeValueAsBytes(cacheRequest)));

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
        given(requestBody.buffer()).willReturn(null);

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
        given(requestBody.buffer()).willReturn(Buffer.buffer());

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
        given(requestBody.buffer()).willReturn(Buffer.buffer(mapper.writeValueAsBytes(cacheRequest)));

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
        given(requestBody.buffer()).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(405));
        verifyNoInteractions(cacheNotificationListener);
    }
}

package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.cache.CoreCacheService;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class GetVtrackHandlerTest extends VertxTest {

    @Mock
    private CoreCacheService coreCacheService;
    @Mock
    private TimeoutFactory timeoutFactory;

    private GetVtrackHandler target;

    @Mock(strictness = LENIENT)
    private RoutingContext routingContext;
    @Mock(strictness = LENIENT)
    private HttpServerRequest httpRequest;
    @Mock(strictness = LENIENT)
    private HttpServerResponse httpResponse;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(AsciiString.class))).willReturn(httpResponse);
        given(httpResponse.putHeader(anyString(), anyString())).willReturn(httpResponse);

        given(httpRequest.getParam("uuid")).willReturn("key");
        given(httpRequest.getParam("ch")).willReturn("test.com");

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        target = new GetVtrackHandler(2000, coreCacheService, timeoutFactory);
    }

    @Test
    public void shouldRespondWithBadRequestWhenAccountParameterIsMissing() {
        // given
        given(httpRequest.getParam("uuid")).willReturn(null);

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(coreCacheService);

        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("'uuid' is a required query parameter and can't be empty");
    }

    @Test
    public void shouldRespondWithInternalServerErrorWhenCacheServiceReturnFailure() {
        // given
        given(coreCacheService.getCachedObject(eq("key"), eq("test.com"), any()))
                .willReturn(Future.failedFuture("error"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(500);
        verify(httpResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end("Error occurred while sending request to cache: error");
    }

    @Test
    public void shouldRespondWithBodyAndHeadersReturnedFromCacheWhenStatusCodeIsOk() {
        // given
        given(coreCacheService.getCachedObject(any(), any(), any())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(200, caseInsensitiveMultiMap().add("Header", "Value"), "body")));

        // when
        target.handle(routingContext);

        // then
        verify(coreCacheService).getCachedObject(eq("key"), eq("test.com"), any());
        verify(httpResponse).setStatusCode(200);
        verify(httpResponse).putHeader("Header", "Value");
        verify(httpResponse).end("body");
    }

    @Test
    public void shouldRespondWithBodyAndDefaultHeadersReturnedFromCacheWhenStatusCodeIsNotOk() {
        // given
        given(coreCacheService.getCachedObject(any(), any(), any())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(404, caseInsensitiveMultiMap().add("Header", "Value"), "reason")));

        // when
        target.handle(routingContext);

        // then
        verify(coreCacheService).getCachedObject(eq("key"), eq("test.com"), any());
        verify(httpResponse).setStatusCode(404);
        verify(httpResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end("reason");
    }
}

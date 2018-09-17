package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class BasicHttpClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private io.vertx.core.http.HttpClient httpClient;
    @Mock
    private HttpClientRequest httpClientRequest;

    private BasicHttpClient basicHttpClient;

    @Before
    public void setUp() {
        given(httpClient.requestAbs(any(), anyString())).willReturn(httpClientRequest);
        given(httpClientRequest.handler(any())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        basicHttpClient = new BasicHttpClient(httpClient);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BasicHttpClient(null));
    }

    @Test
    public void requestShouldPerformHttpRequestWithExpectedParams() {
        // given
        final MultiMap headers = mock(MultiMap.class);
        given(httpClientRequest.headers()).willReturn(headers);

        // when
        basicHttpClient.request(HttpMethod.POST, "uri", headers, "body1", 500L, null, null);

        // then
        verify(httpClient).requestAbs(eq(HttpMethod.POST), eq("uri"));
        verify(httpClientRequest.headers()).addAll(eq(headers));
        verify(httpClientRequest).handler(isNotNull());
        verify(httpClientRequest).exceptionHandler(isNotNull());
        verify(httpClientRequest).setTimeout(eq(500L));
        verify(httpClientRequest).end(eq("body1"));
    }

    @Test
    public void requestShouldFailIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> future = basicHttpClient.request(HttpMethod.GET, "uri", null, null, 0L, null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Request exception");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldCallExceptionHandlerIfHttpRequestFails() {
        // given
        final Handler<HttpClientResponse> responseHandler = mock(Handler.class);
        final Handler<Throwable> exceptionHandler = mock(Handler.class);

        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        basicHttpClient.request(HttpMethod.GET, "uri", null, null, 0L, null, exceptionHandler);

        // then
        verify(exceptionHandler).handle(any());
        verifyZeroInteractions(responseHandler);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldCallResponseHandlerIfHttpRequestSucceeds() {
        // given
        final Handler<HttpClientResponse> responseHandler = mock(Handler.class);
        final Handler<Throwable> exceptionHandler = mock(Handler.class);

        given(httpClientRequest.handler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(mock(HttpClientResponse.class)));

        // when
        basicHttpClient.request(HttpMethod.GET, "uri", null, null, 0L, responseHandler, exceptionHandler);

        // then
        verify(responseHandler).handle(any());
        verifyZeroInteractions(exceptionHandler);
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }
}

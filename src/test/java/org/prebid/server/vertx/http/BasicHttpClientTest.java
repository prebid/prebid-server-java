package org.prebid.server.vertx.http;

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

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class BasicHttpClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private io.vertx.core.http.HttpClient httpClient;

    private BasicHttpClient basicHttpClient;

    @Mock
    private HttpClientRequest httpClientRequest;
    @Mock
    private Handler<HttpClientResponse> responseHandler;
    @Mock
    private Handler<Throwable> exceptionHandler;

    @Before
    public void setUp() {
        given(httpClient.requestAbs(any(), any())).willReturn(httpClientRequest);

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
        basicHttpClient.request(HttpMethod.POST, "url", headers, "body", 500L, responseHandler, exceptionHandler);

        // then
        verify(httpClient).requestAbs(eq(HttpMethod.POST), eq("url"));
        verify(httpClientRequest.headers()).addAll(eq(headers));
        verify(httpClientRequest).setTimeout(eq(500L));
        verify(httpClientRequest).handler(eq(responseHandler));
        verify(httpClientRequest).exceptionHandler(eq(exceptionHandler));
        verify(httpClientRequest).end(eq("body"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldCallExceptionHandlerIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        basicHttpClient.request(HttpMethod.GET, null, null, null, 0L, null, exceptionHandler);

        // then
        verify(exceptionHandler).handle(any());
        verifyZeroInteractions(responseHandler);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldCallResponseHandlerIfHttpRequestSucceeds() {
        // given
        given(httpClientRequest.handler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(mock(HttpClientResponse.class)));

        // when
        basicHttpClient.request(HttpMethod.GET, null, null, null, 0L, responseHandler, exceptionHandler);

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

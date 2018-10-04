package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BasicHttpClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private io.vertx.core.http.HttpClient wrappedHttpClient;

    private BasicHttpClient httpClient;

    @Mock
    private HttpClientRequest httpClientRequest;
    @Mock
    private HttpClientResponse httpClientResponse;

    @Before
    public void setUp() {
        given(wrappedHttpClient.requestAbs(any(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.handler(any())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);
        given(httpClientRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);

        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willReturn(httpClientResponse);

        httpClient = new BasicHttpClient(wrappedHttpClient);
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
        httpClient.request(HttpMethod.POST, "url", headers, "body", 500L);

        // then
        verify(wrappedHttpClient).requestAbs(eq(HttpMethod.POST), eq("url"));
        verify(httpClientRequest.headers()).addAll(eq(headers));
        verify(httpClientRequest).setTimeout(eq(500L));
        verify(httpClientRequest).end(eq("body"));
    }

    @Test
    public void requestShouldSucceedsIfHttpRequestSucceeds() {
        // given
        given(httpClientRequest.handler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(httpClientResponse));

        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer("response")));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, null, 0L);

        // then
        assertThat(future.succeeded()).isTrue();
    }

    @Test
    public void requestShouldFailsIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, null, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("Request exception");
    }

    @Test
    public void requestShouldFailsIfHttpResponseFails() {
        // given
        given(httpClientRequest.handler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(httpClientResponse));

        given(httpClientResponse.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Response exception")));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, null, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("Response exception");
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }
}

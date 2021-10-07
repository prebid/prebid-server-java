package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(VertxUnitRunner.class)
public class BasicHttpClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
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

        given(httpClientRequest.setFollowRedirects(anyBoolean())).willReturn(httpClientRequest);
        given(httpClientRequest.handler(any())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);
        given(httpClientRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willReturn(httpClientResponse);

        httpClient = new BasicHttpClient(vertx, wrappedHttpClient);
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
        verify(httpClientRequest).end(eq("body"));
    }

    @Test
    public void requestShouldSucceedIfHttpRequestSucceeds() {
        // given
        given(httpClientRequest.handler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(httpClientResponse));

        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer("response")));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, (String) null, 1L);

        // then
        assertThat(future.succeeded()).isTrue();
    }

    @Test
    public void requestShouldAllowFollowingRedirections() {
        // when
        httpClient.request(HttpMethod.GET, null, null, (String) null, 1L);

        // then
        verify(httpClientRequest).setFollowRedirects(true);
    }

    @Test
    public void requestShouldFailIfInvalidUrlPassed() {
        // given
        given(wrappedHttpClient.requestAbs(any(), any())).willThrow(new RuntimeException("error"));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, (String) null, 1L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("error");
    }

    @Test
    public void requestShouldFailIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, (String) null, 1L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("Request exception");
    }

    @Test
    public void requestShouldFailIfHttpResponseFails() {
        // given
        given(httpClientRequest.handler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(httpClientResponse));

        given(httpClientResponse.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Response exception")));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, (String) null, 1L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("Response exception");
    }

    @Test
    public void requestShouldFailIfHttpRequestTimedOut(TestContext context) {
        // given
        final Vertx vertx = Vertx.vertx();
        final BasicHttpClient httpClient = new BasicHttpClient(vertx, vertx.createHttpClient());
        final int serverPort = 7777;

        startServer(serverPort, 2000L, 0L);

        // when
        final Async async = context.async();
        final Future<?> future = httpClient.get("http://localhost:" + serverPort, 1000L);
        future.setHandler(ar -> async.complete());
        async.await();

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(TimeoutException.class)
                .hasMessageStartingWith("Timeout period of 1000ms has been exceeded");
    }

    @Test
    public void requestShouldFailIfHttpResponseTimedOut(TestContext context) {
        // given
        final Vertx vertx = Vertx.vertx();
        final BasicHttpClient httpClient = new BasicHttpClient(vertx, vertx.createHttpClient());
        final int serverPort = 8888;

        startServer(serverPort, 0L, 2000L);

        // when
        final Async async = context.async();
        final Future<?> future = httpClient.get("http://localhost:" + serverPort, 1000L);
        future.setHandler(ar -> async.complete());
        async.await();

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(TimeoutException.class)
                .hasMessage("Timeout period of 1000ms has been exceeded");
    }

    /**
     * The server returns entire response or body with delay.
     */
    private static void startServer(int port, long entireResponseDelay, long bodyResponseDelay) {
        final CountDownLatch completionLatch = new CountDownLatch(1);

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                completionLatch.countDown();

                try (Socket clientSocket = serverSocket.accept()) {
                    try (BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(clientSocket.getOutputStream()))) {

                        // waiting for the response
                        if (entireResponseDelay > 0) {
                            sleep(entireResponseDelay);
                        }

                        out.write("HTTP/1.1 200 OK");
                        out.newLine();

                        out.write("Content-Length: 6"); // set body size greater then length of "start" word
                        out.newLine();

                        out.newLine();
                        out.write("start");
                        out.flush();

                        // waiting for the rest of body
                        if (bodyResponseDelay > 0) {
                            sleep(bodyResponseDelay);
                        }

                        out.write("finish");
                        out.flush();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        try {
            completionLatch.await(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

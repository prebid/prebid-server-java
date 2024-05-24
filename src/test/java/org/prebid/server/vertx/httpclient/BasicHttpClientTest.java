package org.prebid.server.vertx.httpclient;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
        given(wrappedHttpClient.request(any())).willReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send()).willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientRequest.send(any(Buffer.class))).willReturn(Future.succeededFuture(httpClientResponse));

        httpClient = new BasicHttpClient(vertx, wrappedHttpClient);
    }

    @Test
    public void requestShouldPerformHttpRequestWithExpectedParams() {
        // given and when
        httpClient.request(HttpMethod.POST, "http://www.example.com", MultiMap.caseInsensitiveMultiMap(), "body", 500L);

        // then
        final ArgumentCaptor<RequestOptions> requestOptionsArgumentCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(wrappedHttpClient).request(requestOptionsArgumentCaptor.capture());

        final RequestOptions expectedRequestOptions = new RequestOptions()
                .setFollowRedirects(true)
                .setConnectTimeout(500L)
                .setMethod(HttpMethod.POST)
                .setAbsoluteURI("http://www.example.com")
                .setHeaders(MultiMap.caseInsensitiveMultiMap());
        assertThat(requestOptionsArgumentCaptor.getValue().toJson()).isEqualTo(expectedRequestOptions.toJson());

        verify(httpClientRequest).send(eq(Buffer.buffer("body".getBytes())));
    }

    @Test
    public void requestShouldSucceedIfHttpRequestSucceeds() {
        // given
        given(httpClientResponse.body()).willReturn(Future.succeededFuture(Buffer.buffer("response")));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, "http://www.example.com", null, (String) null, 1L);

        // then
        assertThat(future.succeeded()).isTrue();
    }

    @Test
    public void requestShouldAllowFollowingRedirections() {
        // given and when
        httpClient.request(HttpMethod.POST, "http://www.example.com", MultiMap.caseInsensitiveMultiMap(), "body", 500L);

        // then
        final ArgumentCaptor<RequestOptions> requestOptionsArgumentCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(wrappedHttpClient).request(requestOptionsArgumentCaptor.capture());
        assertTrue(requestOptionsArgumentCaptor.getValue().getFollowRedirects());
    }

    @Test
    public void requestShouldFailIfInvalidUrlPassed() {
        // given and when
        final Future<?> future = httpClient.request(HttpMethod.GET, null, null, (String) null, 1L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(MalformedURLException.class);
    }

    @Test
    public void requestShouldFailIfHttpRequestFails() {
        // given
        given(wrappedHttpClient.request(any()))
                .willReturn(Future.failedFuture("Request exception"));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, "http://www.example.com", null, (String) null, 1L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("Request exception");
    }

    @Test
    public void requestShouldFailIfHttpResponseFails() {
        // given
        given(wrappedHttpClient.request(any()))
                .willReturn(Future.failedFuture("Response exception"));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, "http://example.coom", null, (String) null, 1L);

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
        future.onComplete(ar -> async.complete());
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
        future.onComplete(ar -> async.complete());
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
}

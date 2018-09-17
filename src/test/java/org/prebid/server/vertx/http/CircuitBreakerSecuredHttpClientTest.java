package org.prebid.server.vertx.http;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.Metrics;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class CircuitBreakerSecuredHttpClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private Vertx vertx;
    @Mock
    private HttpClient wrappedHttpClient;
    @Mock
    private Metrics metrics;

    private CircuitBreakerSecuredHttpClient httpClient;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        httpClient = new CircuitBreakerSecuredHttpClient(vertx, wrappedHttpClient, metrics, 0, 100L, 200L);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredHttpClient(null, null, null, 0, 0L, 0L));
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredHttpClient(vertx, null, null, 0, 0L, 0L));
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredHttpClient(vertx, httpClient, null, 0, 0L, 0L));
    }

    @Test
    public void requestShouldFailsOnInvalidUrl() {
        // when and then
        assertThatThrownBy(() -> httpClient.request(HttpMethod.GET, "invalid_url", null, null, 0L, null, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid url: invalid_url");
    }

    @Test
    public void requestShouldReturnResultIfCircuitIsClosedAndWrappedHttpClientSucceeded(TestContext context) {
        // given
        givenHttpClientReturning(singletonList(
                Future.succeededFuture(givenHttpClientResponse(200, "OK"))));

        // when
        final Future<HttpClientResponse> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null,
                null);

        // then
        future.setHandler(context.asyncAssertSuccess(result ->
                assertThat(result.statusCode()).isEqualTo(200)));
    }

    @Test
    public void requestShouldFailsIfCircuitIsClosedButWrappedHttpClientFails(TestContext context) {
        // given
        givenHttpClientReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, "http://url:8080/path1?a=b", null, null, 0L, null,
                null);

        // then
        future.setHandler(context.asyncAssertFailure(throwable ->
                assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("exception1")));
    }

    @Test
    public void requestShouldFailsIfCircuitIsClosedButWrappedHttpClientReturnsHttpStatus500(TestContext context) {
        // given
        givenHttpClientReturning(singletonList(
                Future.succeededFuture(givenHttpClientResponse(500, "Internal Server Error"))));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null);

        // then
        future.setHandler(context.asyncAssertFailure(throwable ->
                assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("500: Internal Server Error")));
    }

    @Test
    public void requestShouldNotCallWrappedHttpClientIfCircuitIsOpened(TestContext context) {
        // given
        givenHttpClientReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null) // 1 call
                .recover(ignored -> httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null,
                        null)); // 2 call

        // then
        future.setHandler(context.asyncAssertFailure(throwable -> {
            assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

            verify(wrappedHttpClient)
                    .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked only on 1 call
        }));
    }

    @Test
    public void requestShouldFailsIfCircuitIsHalfOpenedAndWrappedHttpClientFails(TestContext context) {
        // given
        givenHttpClientReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Async async = context.async();
        httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null) // 1 call
                .recover(ignored ->
                        httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null)) // 2 call
                .setHandler(ignored -> vertx.setTimer(300L, id -> async.complete()));
        async.await();

        final Future<?> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null); // 3 call

        // then
        future.setHandler(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("exception1");

            verify(wrappedHttpClient, times(2))
                    .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked only on 1 & 3 calls
        }));
    }

    @Test
    public void requestShouldReturnResultIfCircuitIsHalfOpenedAndWrappedHttpClientSucceeded(TestContext context) {
        // given
        givenHttpClientReturning(Arrays.asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture(givenHttpClientResponse(200, "OK"))));

        // when
        final Async async = context.async();
        httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null) // 1 call
                .recover(ignored ->
                        httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null)) // 2 call
                .setHandler(ignored -> vertx.setTimer(300L, id -> async.complete()));
        async.await();

        final Future<HttpClientResponse> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null,
                null); // 3 call

        // then
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.statusCode()).isEqualTo(200);

            verify(wrappedHttpClient, times(2))
                    .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked only on 1 & 3 calls
        }));
    }

    @Test
    public void requestShouldReportMetricsOnCircuitOpened(TestContext context) {
        // given
        givenHttpClientReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null); // 3 call

        // then
        future.setHandler(context.asyncAssertFailure(throwable ->
                verify(metrics).updateHttpClientCircuitBreakerMetric(eq(true))));
    }

    @Test
    public void executeQueryShouldReportMetricsOnCircuitClosed(TestContext context) {
        // given
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(200);
        given(httpClientResponse.statusMessage()).willReturn("OK");
        givenHttpClientReturning(singletonList(Future.succeededFuture(httpClientResponse)));

        given(wrappedHttpClient.request(any(), anyString(), any(), any(), anyLong(), any(), any()))
                .willReturn(Future.succeededFuture(httpClientResponse));


        givenHttpClientReturning(Arrays.asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture(httpClientResponse)));

        // when
        final Async async = context.async();
        httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null) // 1 call
                .recover(ignored ->
                        httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null)) // 2 call
                .setHandler(ignored -> vertx.setTimer(300L, id -> async.complete()));
        async.await();

        final Future<?> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, null, null); // 3 call

        // then
        future.setHandler(context.asyncAssertSuccess(result ->
                verify(metrics).updateHttpClientCircuitBreakerMetric(eq(false))));
    }

    private <T> void givenHttpClientReturning(List<Future<T>> results) {
        BDDMockito.BDDMyOngoingStubbing<Future<?>> stubbing =
                given(wrappedHttpClient.request(any(), anyString(), any(), any(), anyLong(), any(), any()));
        for (Future<T> result : results) {
            stubbing = stubbing.willReturn(result);
        }
    }

    private static HttpClientResponse givenHttpClientResponse(int statusCode, String statusMessage) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(statusCode);
        given(httpClientResponse.statusMessage()).willReturn(statusMessage);
        return httpClientResponse;
    }
}

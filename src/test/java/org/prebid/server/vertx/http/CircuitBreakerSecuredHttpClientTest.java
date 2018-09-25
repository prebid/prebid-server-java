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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
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
        assertThatThrownBy(() -> httpClient.request(HttpMethod.GET, "invalid_url", null, null, 0L))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid url: invalid_url");
    }

    @Test
    public void requestShouldSucceedsIfCircuitIsClosedAndWrappedHttpClientSucceeds(TestContext context) {
        // given
        givenHttpClientReturning(mock(HttpClientResponse.class));

        // when
        final Future<?> future = doRequest(context);

        // then
        verify(wrappedHttpClient).request(any(), anyString(), any(), any(), anyLong());

        assertThat(future.succeeded()).isTrue();
    }

    @Test
    public void requestShouldFailsIfCircuitIsClosedButWrappedHttpClientFails(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        final Future<?> future = doRequest(context);

        // then
        verify(wrappedHttpClient).request(any(), anyString(), any(), any(), anyLong());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");
    }

    @Test
    public void requestShouldFailsIfCircuitIsHalfOpenedButWrappedHttpClientFailsAndResetTimeIsNotPassedBy(
            TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        final Future<?> future1 = doRequest(context); // 1 call
        final Future<?> future2 = doRequest(context); // 2 call

        // then
        verify(wrappedHttpClient).request(any(), anyString(), any(), any(), anyLong()); // invoked only on 1 call

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");
    }

    @Test
    public void requestShouldFailsIfCircuitIsHalfOpenedButWrappedHttpClientFails(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        final Future<?> future1 = doRequest(context); // 1 call
        final Future<?> future2 = doRequest(context); // 2 call
        doWaitForResetTime(context);
        final Future<?> future3 = doRequest(context); // 3 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), any(), anyLong()); // invoked only on 1 & 3 calls

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.failed()).isTrue();
        assertThat(future3.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");
    }

    @Test
    public void requestShouldSucceedsIfCircuitIsHalfOpenedAndWrappedHttpClientSucceeds(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception"), mock(HttpClientResponse.class));

        // when
        final Future<?> future1 = doRequest(context); // 1 call
        final Future<?> future2 = doRequest(context); // 2 call
        doWaitForResetTime(context);
        final Future<?> future3 = doRequest(context); // 3 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), any(), anyLong()); // invoked only on 1 & 3 calls

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.succeeded()).isTrue();
    }

    @Test
    public void requestShouldReportMetricsOnCircuitOpened(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        doRequest(context);

        // then
        verify(metrics).updateHttpClientCircuitBreakerMetric(eq(true));
    }

    @Test
    public void executeQueryShouldReportMetricsOnCircuitClosed(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception"), mock(HttpClientResponse.class));

        // when
        doRequest(context); // 1 call
        doRequest(context); // 2 call
        doWaitForResetTime(context);
        doRequest(context); // 3 call

        // then
        verify(metrics).updateHttpClientCircuitBreakerMetric(eq(false));
    }

    @SuppressWarnings("unchecked")
    private <T> void givenHttpClientReturning(T... results) {
        BDDMockito.BDDMyOngoingStubbing<Future<HttpClientResponse>> stubbing =
                given(wrappedHttpClient.request(any(), anyString(), any(), any(), anyLong()));
        for (T result : results) {
            if (result instanceof Exception) {
                stubbing = stubbing.willReturn(Future.failedFuture((Throwable) result));
            } else {
                stubbing = stubbing.willReturn(Future.succeededFuture((HttpClientResponse) result));
            }
        }
    }

    private Future<HttpClientResponse> doRequest(TestContext context) {
        final Future<HttpClientResponse> future = httpClient.request(HttpMethod.GET, "http://url", null, null, 0L);

        final Async async = context.async();
        future.setHandler(ar -> async.complete());
        async.await();

        return future;
    }

    private void doWaitForResetTime(TestContext context) {
        final Async async = context.async();
        vertx.setTimer(300L, id -> async.complete()); // waiting for reset time of circuit breaker
        async.await();
    }
}

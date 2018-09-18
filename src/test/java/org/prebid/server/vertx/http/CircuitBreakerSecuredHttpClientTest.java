package org.prebid.server.vertx.http;

import io.vertx.core.Handler;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.Metrics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

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

    @Mock
    private Handler<HttpClientResponse> responseHandler;
    @Mock
    private Handler<Throwable> exceptionHandler;

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
    public void requestShouldSucceedsIfCircuitIsClosedAndWrappedHttpClientSucceeds(TestContext context) {
        // given
        givenHttpClientReturning(mock(HttpClientResponse.class));

        // when
        doRequest(context, responseHandler); // 1 call
        doRequest(context, responseHandler); // 2 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked on 1 & 2 calls

        verify(responseHandler, times(2)).handle(any());
        verifyZeroInteractions(exceptionHandler);
    }

    @Test
    public void requestShouldFailsIfCircuitIsClosedButWrappedHttpClientFails(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception1"));

        // when
        doRequest(context, exceptionHandler); // 1 call
        doRequest(context, exceptionHandler); // 2 call

        // then
        verify(wrappedHttpClient)
                .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked only on 1 call

        verifyZeroInteractions(responseHandler);

        final ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionHandler, times(2)).handle(captor.capture());
        assertThat(captor.getAllValues()).extracting(Throwable::getMessage)
                .containsExactly("exception1", "open circuit");
    }

    @Test
    public void requestShouldFailsIfCircuitIsClosedButWrappedHttpClientReturnsHttpStatus500(TestContext context) {
        // given
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(500);
        given(httpClientResponse.statusMessage()).willReturn("Internal Server Error");
        givenHttpClientReturning(httpClientResponse);

        // when
        doRequest(context, exceptionHandler); // 1 call
        doRequest(context, exceptionHandler); // 2 call

        // then
        verify(wrappedHttpClient)
                .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked only on 1 call

        verifyZeroInteractions(responseHandler);

        final ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionHandler, times(2)).handle(captor.capture());
        assertThat(captor.getAllValues()).extracting(Throwable::getMessage)
                .containsExactly("500: Internal Server Error", "open circuit");
    }

    @Test
    public void requestShouldFailsIfCircuitIsHalfOpenedButWrappedHttpClientFails(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception1"));

        // when
        doRequest(context, exceptionHandler); // 1 call
        doRequest(context, exceptionHandler); // 2 call

        final Async async = context.async();
        vertx.setTimer(300L, id -> async.countDown());
        async.await();

        doRequest(context, exceptionHandler); // 3 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked only on 1 & 3 calls

        verifyZeroInteractions(responseHandler);

        final ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionHandler, times(3)).handle(captor.capture());
        assertThat(captor.getAllValues()).extracting(Throwable::getMessage)
                .containsExactly("exception1", "open circuit", "exception1");
    }

    @Test
    public void requestShouldSucceedsIfCircuitIsHalfOpenedAndWrappedHttpClientSucceeds(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception1"), mock(HttpClientResponse.class));

        // when
        doRequest(context, exceptionHandler); // 1 call
        doRequest(context, exceptionHandler); // 2 call

        final Async async = context.async();
        vertx.setTimer(300L, id -> async.countDown()); // waiting for reset time of circuit breaker
        async.await();

        doRequest(context, responseHandler); // 3 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), any(), anyLong(), any(), any()); // invoked only on 1 & 3 calls

        verify(responseHandler).handle(any());

        final ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionHandler, times(2)).handle(captor.capture());
        assertThat(captor.getAllValues()).extracting(Throwable::getMessage)
                .containsExactly("exception1", "open circuit");
    }

    @Test
    public void requestShouldReportMetricsOnCircuitOpened(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception1"));

        // when
        doRequest(context, exceptionHandler);

        // then
        verify(metrics).updateHttpClientCircuitBreakerMetric(eq(true));
    }

    @Test
    public void executeQueryShouldReportMetricsOnCircuitClosed(TestContext context) {
        // given
        givenHttpClientReturning(new RuntimeException("exception1"), mock(HttpClientResponse.class));

        // when
        doRequest(context, exceptionHandler); // 1 call
        doRequest(context, exceptionHandler); // 2 call

        final Async async = context.async();
        vertx.setTimer(300L, id -> async.countDown()); // waiting for reset time of circuit breaker
        async.await();

        doRequest(context, responseHandler); // 3 call

        // then
        verify(metrics).updateHttpClientCircuitBreakerMetric(eq(false));
    }

    @SuppressWarnings("unchecked")
    private <T> void givenHttpClientReturning(T... results) {
        Stubber stubber = null;
        for (T result : results) {
            stubber = stubber == null
                    ? doAnswer(withSelfAndPassObjectToHandler(result))
                    : stubber.doAnswer(withSelfAndPassObjectToHandler(result));
        }
        if (stubber != null) {
            stubber.when(wrappedHttpClient).request(any(), anyString(), any(), any(), anyLong(), any(), any());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        final int argIndex = obj instanceof Exception ? 6 : 5; // response handler or exception handler
        return inv -> {
            // invoking passed handler right away passing mock object to it
            ((Handler<T>) inv.getArgument(argIndex)).handle(obj);
            return inv.getMock();
        };
    }

    private Answer<Object> withSelfAndCountDownAsync(Async async) {
        return inv -> {
            async.countDown();
            return inv.getMock();
        };
    }

    private void doRequest(TestContext context, Handler<?> handler) {
        Async async = context.async();
        doAnswer(withSelfAndCountDownAsync(async)).when(handler).handle(any());
        httpClient.request(HttpMethod.GET, "http://url", null, null, 0L, responseHandler, exceptionHandler);
        async.await();
    }
}

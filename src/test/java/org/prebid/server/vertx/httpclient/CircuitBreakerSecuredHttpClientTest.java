package org.prebid.server.vertx.httpclient;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
@RunWith(VertxUnitRunner.class)
public class CircuitBreakerSecuredHttpClientTest {

    private Vertx vertx;

    private Clock clock;
    @Mock
    private HttpClient wrappedHttpClient;
    @Mock
    private Metrics metrics;

    private CircuitBreakerSecuredHttpClient httpClient;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        httpClient = new CircuitBreakerSecuredHttpClient(vertx, wrappedHttpClient, metrics, 1, 100L, 200L, 24, clock);
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertx.close(context.succeedingThenComplete());
    }

    @Test
    public void requestShouldFailOnInvalidUrl() {
        // when and then
        assertThatThrownBy(() -> httpClient.request(HttpMethod.GET, "invalid_url", null, (String) null, 0L))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid url: invalid_url");
    }

    @Test
    public void requestShouldSucceedIfCircuitIsClosedAndWrappedHttpClientSucceeds() {
        // given
        givenHttpClientReturning(HttpClientResponse.of(200, null, null));

        // when
        final Future<?> future = doRequest();

        // then
        verify(wrappedHttpClient).request(any(), anyString(), any(), (String) any(), anyLong(), anyLong());

        assertThat(future.succeeded()).isTrue();
    }

    @Test
    public void requestShouldFailIfCircuitIsClosedButWrappedHttpClientFails() {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        final Future<?> future = doRequest();

        // then
        verify(wrappedHttpClient).request(any(), anyString(), any(), (String) any(), anyLong(), anyLong());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");
    }

    @Test
    public void requestShouldFailIfCircuitIsHalfOpenedButWrappedHttpClientFailsAndClosingTimeIsNotPassedBy() {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        final Future<?> future1 = doRequest(); // 1 call
        final Future<?> future2 = doRequest(); // 2 call

        // then
        // invoked only on 1 call
        verify(wrappedHttpClient).request(any(), anyString(), any(), (String) any(), anyLong(), anyLong());

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");
    }

    @Test
    public void requestShouldFailIfCircuitIsHalfOpenedButWrappedHttpClientFails() {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        final Future<?> future1 = doRequest(); // 1 call
        final Future<?> future2 = doRequest(); // 2 call
        doWaitForClosingInterval();
        final Future<?> future3 = doRequest(); // 3 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), (String) any(), anyLong(), anyLong()); // invoked only on 1 & 3 calls

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.failed()).isTrue();
        assertThat(future3.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");
    }

    @Test
    public void requestShouldSucceedIfCircuitIsHalfOpenedAndWrappedHttpClientSucceeds() {
        // given
        givenHttpClientReturning(new RuntimeException("exception"), HttpClientResponse.of(200, null, null));

        // when
        final Future<?> future1 = doRequest(); // 1 call
        final Future<?> future2 = doRequest(); // 2 call
        doWaitForClosingInterval();
        final Future<?> future3 = doRequest(); // 3 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), (String) any(), anyLong(), anyLong()); // invoked only on 1 & 3 calls

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.succeeded()).isTrue();
    }

    @Test
    public void requestShouldFailWithOriginalExceptionIfOpeningIntervalExceeds() {
        // given
        httpClient = new CircuitBreakerSecuredHttpClient(vertx, wrappedHttpClient, metrics, 2, 100L, 200L, 24, clock);

        givenHttpClientReturning(new RuntimeException("exception1"), new RuntimeException("exception2"));

        // when
        final Future<?> future1 = doRequest(); // 1 call
        doWaitForOpeningInterval();
        final Future<?> future2 = doRequest(); // 2 call

        // then
        verify(wrappedHttpClient, times(2))
                .request(any(), anyString(), any(), (String) any(), anyLong(), anyLong()); // invoked on 1 & 2 calls

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception2");
    }

    @Test
    public void circuitBreakerNumberGaugeShouldReportActualNumber() {
        // when
        doRequest();

        // then
        final ArgumentCaptor<LongSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(LongSupplier.class);
        verify(metrics).createHttpClientCircuitBreakerNumberGauge(gaugeValueProviderCaptor.capture());
        final LongSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        assertThat(gaugeValueProvider.getAsLong()).isEqualTo(1);
    }

    @Test
    public void circuitBreakerGaugeShouldReportOpenedWhenCircuitOpen() {
        // given
        givenHttpClientReturning(new RuntimeException("exception"));

        // when
        doRequest();

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createHttpClientCircuitBreakerGauge(
                eq("http_url"),
                gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        assertThat(gaugeValueProvider.getAsBoolean()).isTrue();
    }

    @Test
    public void circuitBreakerGaugeShouldReportClosedWhenCircuitClosed() {
        // given
        givenHttpClientReturning(new RuntimeException("exception"), HttpClientResponse.of(200, null, null));

        // when
        doRequest(); // 1 call
        doRequest(); // 2 call
        doWaitForClosingInterval();
        doRequest(); // 3 call

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createHttpClientCircuitBreakerGauge(
                eq("http_url"),
                gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        assertThat(gaugeValueProvider.getAsBoolean()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private <T> void givenHttpClientReturning(T... results) {
        BDDMockito.BDDMyOngoingStubbing<Future<HttpClientResponse>> stubbing =
                given(wrappedHttpClient.request(any(), anyString(), any(),
                        (String) any(), anyLong(), anyLong()));
        for (T result : results) {
            if (result instanceof Exception) {
                stubbing = stubbing.willReturn(Future.failedFuture((Throwable) result));
            } else {
                stubbing = stubbing.willReturn(Future.succeededFuture((HttpClientResponse) result));
            }
        }
    }

    private Future<HttpClientResponse> doRequest() {
        final Future<HttpClientResponse> future = httpClient
                .request(HttpMethod.GET, "http://url", null, (String) null, 0L);

        final Promise<?> promise = Promise.promise();
        future.onComplete(ar -> promise.complete());
        promise.future().toCompletionStage().toCompletableFuture().join();

        return future;
    }

    private void doWaitForOpeningInterval() {
        doWait(150L);
    }

    private void doWaitForClosingInterval() {
        doWait(250L);
    }

    private void doWait(long timeout) {
        final Promise<?> promise = Promise.promise();
        vertx.setTimer(timeout, id -> promise.complete());
        promise.future().toCompletionStage().toCompletableFuture().join();
    }
}

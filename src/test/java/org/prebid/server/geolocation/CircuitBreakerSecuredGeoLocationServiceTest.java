package org.prebid.server.geolocation;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
@RunWith(VertxUnitRunner.class)
public class CircuitBreakerSecuredGeoLocationServiceTest {

    private Vertx vertx;

    private Clock clock;
    @Mock
    private GeoLocationService wrappedGeoLocationService;
    @Mock
    private Metrics metrics;

    private CircuitBreakerSecuredGeoLocationService geoLocationService;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        geoLocationService = new CircuitBreakerSecuredGeoLocationService(vertx, wrappedGeoLocationService, metrics, 1,
                100L, 200L, clock);
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertx.close(context.succeedingThenComplete());
    }

    @Test
    public void lookupShouldSucceedsIfCircuitIsClosedAndWrappedGeoLocationSucceeds() {
        // given
        givenWrappedGeoLocationReturning(
                Future.succeededFuture(GeoInfo.builder().vendor("vendor").country("country").build()));

        // when
        final Future<GeoInfo> future = doLookup();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getCountry()).isEqualTo("country");

        verify(wrappedGeoLocationService).lookup(any(), any());
    }

    @Test
    public void lookupShouldFailsIfCircuitIsClosedButWrappedGeoLocationFails() {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        final Future<GeoInfo> future = doLookup();

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        verify(wrappedGeoLocationService).lookup(any(), any());
    }

    @Test
    public void lookupShouldFailsIfCircuitIsOpened() {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        doLookup(); // 1 call
        final Future<?> future = doLookup(); // 2 call

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        verify(wrappedGeoLocationService).lookup(any(), any()); // invoked only on 1 call
    }

    @Test
    public void lookupShouldFailsIfCircuitIsHalfOpenedButWrappedGeoLocationFails() {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        doLookup(); // 1 call
        doLookup(); // 2 call
        doWaitForClosingInterval();
        final Future<?> future = doLookup(); // 3 call

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        verify(wrappedGeoLocationService, times(2)).lookup(any(), any()); // invoked only on 1 & 3 calls
    }

    @Test
    public void lookupShouldSucceedsIfCircuitIsHalfOpenedAndWrappedGeoLocationSucceeds() {
        // given
        givenWrappedGeoLocationReturning(
                Future.failedFuture(new RuntimeException("exception")),
                Future.succeededFuture(GeoInfo.builder().vendor("vendor").country("country").build()));

        // when
        doLookup(); // 1 call
        doLookup(); // 2 call
        doWaitForClosingInterval();
        final Future<GeoInfo> future = doLookup(); // 3 call

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getCountry()).isEqualTo("country");

        verify(wrappedGeoLocationService, times(2)).lookup(any(), any()); // invoked only on 1 & 3 calls
    }

    @Test
    public void lookupShouldFailsWithOriginalExceptionIfOpeningIntervalExceeds() {
        // given
        geoLocationService = new CircuitBreakerSecuredGeoLocationService(vertx, wrappedGeoLocationService, metrics, 2,
                100L, 200L, clock);

        givenWrappedGeoLocationReturning(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.failedFuture(new RuntimeException("exception2")));

        // when
        final Future<?> future1 = doLookup(); // 1 call
        doWaitForOpeningInterval();
        final Future<?> future2 = doLookup(); // 2 call

        // then
        verify(wrappedGeoLocationService, times(2)).lookup(any(), any());

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception2");
    }

    @Test
    public void circuitBreakerGaugeShouldReportOpenedWhenCircuitOpen() {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        doLookup();

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createGeoLocationCircuitBreakerGauge(gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        assertThat(gaugeValueProvider.getAsBoolean()).isTrue();
    }

    @Test
    public void circuitBreakerGaugeShouldReportClosedWhenCircuitClosed() {
        // given
        givenWrappedGeoLocationReturning(
                Future.failedFuture(new RuntimeException("exception")),
                Future.succeededFuture(GeoInfo.builder().vendor("vendor").country("country").build()));

        // when
        doLookup(); // 1 call
        doWaitForClosingInterval();
        doLookup(); // 2 call

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createGeoLocationCircuitBreakerGauge(gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        assertThat(gaugeValueProvider.getAsBoolean()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private <T> void givenWrappedGeoLocationReturning(Future<T>... results) {
        BDDMockito.BDDMyOngoingStubbing<Future<GeoInfo>> given =
                given(wrappedGeoLocationService.lookup(any(), any()));
        for (Future<T> result : results) {
            given = given.willReturn((Future<GeoInfo>) result);
        }
    }

    private Future<GeoInfo> doLookup() {
        final Promise<?> promise = Promise.promise();

        final Future<GeoInfo> future = geoLocationService.lookup(null, null);
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

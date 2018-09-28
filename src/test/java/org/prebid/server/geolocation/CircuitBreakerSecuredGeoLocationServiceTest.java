package org.prebid.server.geolocation;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(VertxUnitRunner.class)
public class CircuitBreakerSecuredGeoLocationServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private Vertx vertx;
    @Mock
    private GeoLocationService wrappedGeoLocationService;
    @Mock
    private Metrics metrics;

    private CircuitBreakerSecuredGeoLocationService geoLocationService;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        geoLocationService = new CircuitBreakerSecuredGeoLocationService(vertx, wrappedGeoLocationService, metrics, 0,
                100L, 200L);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredGeoLocationService(null, null, null, 0, 0L, 0L));
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredGeoLocationService(vertx, null, null, 0, 0L, 0L));
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredGeoLocationService(vertx, wrappedGeoLocationService, null, 0, 0L, 0L));
    }

    @Test
    public void lookupShouldSucceedsIfCircuitIsClosedAndWrappedGeoLocationSucceeds(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(Future.succeededFuture(GeoInfo.of("country")));

        // when
        final Future<GeoInfo> future = doLookup(context);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getCountry()).isEqualTo("country");

        verify(wrappedGeoLocationService).lookup(any(), any());
    }

    @Test
    public void lookupShouldFailsIfCircuitIsClosedButWrappedGeoLocationFails(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        final Future<GeoInfo> future = doLookup(context);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        verify(wrappedGeoLocationService).lookup(any(), any());
    }

    @Test
    public void lookupShouldFailsIfCircuitIsOpened(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        doLookup(context); // 1 call
        final Future<?> future = doLookup(context); // 2 call

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        verify(wrappedGeoLocationService).lookup(any(), any()); // invoked only on 1 call
    }

    @Test
    public void lookupShouldFailsIfCircuitIsHalfOpenedButWrappedGeoLocationFails(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        doLookup(context); // 1 call
        doLookup(context); // 2 call
        doWaitForClosingInterval(context);
        final Future<?> future = doLookup(context); // 3 call

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");

        verify(wrappedGeoLocationService, times(2)).lookup(any(), any()); // invoked only on 1 & 3 calls
    }

    @Test
    public void lookupShouldSucceedsIfCircuitIsHalfOpenedAndWrappedGeoLocationSucceeds(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(
                Future.failedFuture(new RuntimeException("exception")),
                Future.succeededFuture(GeoInfo.of("country")));

        // when
        doLookup(context); // 1 call
        doLookup(context); // 2 call
        doWaitForClosingInterval(context);
        final Future<GeoInfo> future = doLookup(context); // 3 call

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getCountry()).isEqualTo("country");

        verify(wrappedGeoLocationService, times(2)).lookup(any(), any()); // invoked only on 1 & 3 calls
    }

    @Test
    public void lookupShouldFailsWithOriginalExceptionIfOpeningIntervalExceeds(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.failedFuture(new RuntimeException("exception2")));

        // when
        final Future<?> future1 = doLookup(context); // 1 call
        doWaitForOpeningInterval(context);
        final Future<?> future2 = doLookup(context); // 2 call

        // then
        verify(wrappedGeoLocationService, times(2)).lookup(any(), any());

        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception2");
    }

    @Test
    public void lookupShouldReportMetricsOnCircuitOpened(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(Future.failedFuture(new RuntimeException("exception")));

        // when
        doLookup(context);

        // then
        verify(metrics).updateGeoLocationCircuitBreakerMetric(eq(true));
    }

    @Test
    public void lookupShouldReportMetricsOnCircuitClosed(TestContext context) {
        // given
        givenWrappedGeoLocationReturning(
                Future.failedFuture(new RuntimeException("exception")),
                Future.succeededFuture(GeoInfo.of("country")));

        // when
        doLookup(context); // 1 call
        doWaitForClosingInterval(context);
        doLookup(context); // 2 call

        // then
        verify(metrics).updateGeoLocationCircuitBreakerMetric(eq(false));
    }

    @SuppressWarnings("unchecked")
    private <T> void givenWrappedGeoLocationReturning(Future... results) {
        BDDMockito.BDDMyOngoingStubbing<Future<GeoInfo>> given =
                given(wrappedGeoLocationService.lookup(any(), any()));
        for (Future<T> result : results) {
            given = given.willReturn((Future<GeoInfo>) result);
        }
    }

    private Future<GeoInfo> doLookup(TestContext context) {
        final Async async = context.async();

        final Future<GeoInfo> future = geoLocationService.lookup(null, null);
        future.setHandler(ar -> async.complete());

        async.await();
        return future;
    }

    private void doWaitForOpeningInterval(TestContext context) {
        doWait(context, 200L);
    }

    private void doWaitForClosingInterval(TestContext context) {
        doWait(context, 300L);
    }

    private void doWait(TestContext context, long timeout) {
        final Async async = context.async();
        vertx.setTimer(timeout, id -> async.complete());
        async.await();
    }
}

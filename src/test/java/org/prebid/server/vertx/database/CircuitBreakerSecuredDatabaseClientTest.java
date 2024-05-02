package org.prebid.server.vertx.database;

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
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.BooleanSupplier;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(VertxUnitRunner.class)
public class CircuitBreakerSecuredDatabaseClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private Vertx vertx;

    private Clock clock;
    @Mock
    private DatabaseClient wrappedDatabaseClient;
    @Mock
    private Metrics metrics;

    private CircuitBreakerSecuredDatabaseClient target;

    private Timeout timeout;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);

        target = new CircuitBreakerSecuredDatabaseClient(vertx, wrappedDatabaseClient, metrics, 1, 100L, 200L, clock);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void executeQueryShouldReturnResultIfCircuitIsClosedAndQuerySucceeded(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.succeededFuture("value")));

        // when
        final Future<?> future = target.executeQuery(
                "query",
                emptyList(),
                rs -> rs.iterator().next().getString(0),
                timeout);

        // then
        future.onComplete(context.asyncAssertSuccess(result -> assertThat(result).isEqualTo("value")));
    }

    @Test
    public void executeQueryShouldReturnExceptionIfCircuitIsClosedAndQueryFails(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        future.onComplete(context.asyncAssertFailure(throwable ->
                assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("exception1")));
    }

    @Test
    public void executeQueryShouldNotExecuteQueryIfCircuitIsOpened(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)); // 2 call

        // then
        future.onComplete(context.asyncAssertFailure(throwable -> {
            assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

            verify(wrappedDatabaseClient)
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 call
        }));
    }

    @Test
    public void executeQueryShouldReturnExceptionIfCircuitIsHalfOpenedAndQueryFails(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Async async = context.async();
        target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .onComplete(ignored -> vertx.setTimer(201L, id -> async.complete()));
        async.await();

        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout); // 3 call

        // then
        future.onComplete(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("exception1");

            verify(wrappedDatabaseClient, times(2))
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 & 3 calls
        }));
    }

    @Test
    public void executeQueryShouldReturnResultIfCircuitIsHalfOpenedAndQuerySucceeded(TestContext context) {
        // given
        givenExecuteQueryReturning(asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture("value")));

        // when
        final Async async = context.async();
        target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .onComplete(ignored -> vertx.setTimer(201L, id -> async.complete()));
        async.await();

        final Future<?> future = target.executeQuery(
                "query",
                emptyList(),
                rs -> rs.iterator().next().getString(0),
                timeout); // 3 call

        // then
        future.onComplete(context.asyncAssertSuccess(result -> {
            assertThat(result).isEqualTo("value");

            verify(wrappedDatabaseClient, times(2))
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 & 3 calls
        }));
    }

    @Test
    public void executeQueryShouldFailsWithOriginalExceptionIfOpeningIntervalExceeds(TestContext context) {
        // given
        target = new CircuitBreakerSecuredDatabaseClient(vertx, wrappedDatabaseClient, metrics, 2, 100L, 200L, clock);

        givenExecuteQueryReturning(asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.failedFuture(new RuntimeException("exception2"))));

        // when
        final Async async = context.async();
        final Future<?> future1 = target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .onComplete(ignored -> vertx.setTimer(101L, id -> async.complete()));
        async.await();

        final Future<?> future2 = target.executeQuery("query", emptyList(), identity(), timeout); // 2 call

        // then
        future1.onComplete(context.asyncAssertFailure(exception ->
                assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("exception1")));

        future2.onComplete(context.asyncAssertFailure(exception ->
                assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("exception2")));

        verify(wrappedDatabaseClient, times(2))
                .executeQuery(any(), any(), any(), any());
    }

    @Test
    public void circuitBreakerGaugeShouldReportOpenedWhenCircuitOpen(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createDatabaseCircuitBreakerGauge(gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        future.onComplete(context.asyncAssertFailure(throwable ->
                assertThat(gaugeValueProvider.getAsBoolean()).isTrue()));
    }

    @Test
    public void circuitBreakerGaugeShouldReportClosedWhenCircuitClosed(TestContext context) {
        // given
        givenExecuteQueryReturning(asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture()));

        // when
        final Async async = context.async();
        target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .onComplete(ignored -> vertx.setTimer(201L, id -> async.complete()));
        async.await();

        final Future<?> future = target.executeQuery(
                "query",
                emptyList(),
                rs -> rs.iterator().next().getString(0),
                timeout); // 3 call

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createDatabaseCircuitBreakerGauge(gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        future.onComplete(context.asyncAssertSuccess(throwable ->
                assertThat(gaugeValueProvider.getAsBoolean()).isFalse()));
    }

    @SuppressWarnings("unchecked")
    private <T> void givenExecuteQueryReturning(List<Future<T>> results) {
        BDDMockito.BDDMyOngoingStubbing<Future<Object>> given =
                given(wrappedDatabaseClient.executeQuery(any(), any(), any(), any()));
        for (Future<T> result : results) {
            given = given.willReturn((Future<Object>) result);
        }
    }
}

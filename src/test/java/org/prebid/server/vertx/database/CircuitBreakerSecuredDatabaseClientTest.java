package org.prebid.server.vertx.database;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.junit5.Checkpoint;
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

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
@RunWith(VertxUnitRunner.class)
public class CircuitBreakerSecuredDatabaseClientTest {

    private Vertx vertx;

    private Clock clock;
    @Mock
    private DatabaseClient wrappedDatabaseClient;
    @Mock
    private Metrics metrics;

    private CircuitBreakerSecuredDatabaseClient target;

    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);

        target = new CircuitBreakerSecuredDatabaseClient(vertx, wrappedDatabaseClient, metrics, 1, 100L, 200L, clock);
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertx.close(context.succeedingThenComplete());
    }

    @Test
    public void executeQueryShouldReturnResultIfCircuitIsClosedAndQuerySucceeded(VertxTestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(Future.succeededFuture("value")));

        // when
        final Future<?> future = target.executeQuery(
                "query",
                emptyList(),
                rs -> rs.iterator().next().getString(0),
                timeout);

        // then
        future.onComplete(context.succeeding(result -> {
            assertThat(result).isEqualTo("value");
            context.completeNow();
        }));
    }

    @Test
    public void executeQueryShouldReturnExceptionIfCircuitIsClosedAndQueryFails(VertxTestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        future.onComplete(context.failing(throwable -> {
            assertThat(throwable)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("exception1");
            context.completeNow();
        }));
    }

    @Test
    public void executeQueryShouldNotExecuteQueryIfCircuitIsOpened(VertxTestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)); // 2 call

        // then
        future.onComplete(context.failing(throwable -> {
            assertThat(throwable)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("open circuit");

            verify(wrappedDatabaseClient)
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 call

            context.completeNow();
        }));
    }

    @Test
    public void executeQueryShouldReturnExceptionIfCircuitIsHalfOpenedAndQueryFails(VertxTestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Promise<?> promise = Promise.promise();
        target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .onComplete(ignored -> vertx.setTimer(201L, id -> promise.complete()));
        promise.future().toCompletionStage().toCompletableFuture().join();

        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout); // 3 call

        // then
        future.onComplete(context.failing(exception -> {
            assertThat(exception)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("exception1");

            verify(wrappedDatabaseClient, times(2))
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 & 3 calls

            context.completeNow();
        }));
    }

    @Test
    public void executeQueryShouldReturnResultIfCircuitIsHalfOpenedAndQuerySucceeded(VertxTestContext context) {
        // given
        givenExecuteQueryReturning(asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture("value")));

        // when
        final Promise<?> promise = Promise.promise();
        target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .onComplete(ignored -> vertx.setTimer(201L, id -> promise.complete()));
        promise.future().toCompletionStage().toCompletableFuture().join();

        final Future<?> future = target.executeQuery(
                "query",
                emptyList(),
                rs -> rs.iterator().next().getString(0),
                timeout); // 3 call

        // then
        future.onComplete(context.succeeding(result -> {
            assertThat(result).isEqualTo("value");

            verify(wrappedDatabaseClient, times(2))
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 & 3 calls

            context.completeNow();
        }));
    }

    @Test
    public void executeQueryShouldFailsWithOriginalExceptionIfOpeningIntervalExceeds(VertxTestContext context) {
        // given
        target = new CircuitBreakerSecuredDatabaseClient(vertx, wrappedDatabaseClient, metrics, 2, 100L, 200L, clock);

        givenExecuteQueryReturning(asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.failedFuture(new RuntimeException("exception2"))));

        // when
        final Promise<?> promise = Promise.promise();
        final Future<?> future1 = target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .onComplete(ignored -> vertx.setTimer(101L, id -> promise.complete()));
        promise.future().toCompletionStage().toCompletableFuture().join();

        final Future<?> future2 = target.executeQuery("query", emptyList(), identity(), timeout); // 2 call

        // then
        final Checkpoint checkpoint1 = context.checkpoint();
        final Checkpoint checkpoint2 = context.checkpoint();
        final Checkpoint checkpoint3 = context.checkpoint();

        future1.onComplete(context.failing(exception -> {
            assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("exception1");
            checkpoint1.flag();
        }));

        future2.onComplete(context.failing(exception -> {
            assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("exception2");
            checkpoint2.flag();
        }));

        Future.any(future1, future2).onComplete(ignored -> {
            verify(wrappedDatabaseClient, times(2))
                    .executeQuery(any(), any(), any(), any());
            checkpoint3.flag();
        });
    }

    @Test
    public void circuitBreakerGaugeShouldReportOpenedWhenCircuitOpen(VertxTestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createDatabaseCircuitBreakerGauge(gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        future.onComplete(context.failing(throwable -> {
            assertThat(gaugeValueProvider.getAsBoolean()).isTrue();
            context.completeNow();
        }));
    }

    @Test
    public void circuitBreakerGaugeShouldReportClosedWhenCircuitClosed(VertxTestContext context) {
        // given
        givenExecuteQueryReturning(asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture()));

        // when
        final Promise<?> promise = Promise.promise();
        target.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> target.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .onComplete(ignored -> vertx.setTimer(201L, id -> promise.complete()));
        promise.future().toCompletionStage().toCompletableFuture().join();

        final Future<?> future = target.executeQuery(
                "query",
                emptyList(),
                rs -> rs.iterator().next().getString(0),
                timeout); // 3 call

        // then
        final ArgumentCaptor<BooleanSupplier> gaugeValueProviderCaptor = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(metrics).createDatabaseCircuitBreakerGauge(gaugeValueProviderCaptor.capture());
        final BooleanSupplier gaugeValueProvider = gaugeValueProviderCaptor.getValue();

        future.onComplete(context.succeeding(throwable -> {
            assertThat(gaugeValueProvider.getAsBoolean()).isFalse();
            context.completeNow();
        }));
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

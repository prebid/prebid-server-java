package org.prebid.server.vertx;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
public class CircuitBreakerTest {

    private Vertx vertx;

    private Clock clock;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        circuitBreaker = new CircuitBreaker("name", vertx, 1, 100L, 200L, clock);
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertx.close(context.succeedingThenComplete());
    }

    @Test
    public void executeShouldSucceedsIfOperationSucceeds() {
        // when
        final Future<?> future = executeWithSuccess("value");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("value");
    }

    @Test
    public void executeShouldFailsIfCircuitIsClosedAndOperationFails() {
        // when
        final Future<?> future = executeWithFail("exception");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");
    }

    @Test
    public void executeShouldFailsIfCircuitIsHalfOpenedAndOperationFailsAndClosingTimeIsNotPassedBy() {
        // when
        final Future<?> future1 = executeWithFail("exception1");
        final Future<?> future2 = executeWithFail(null);

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");
    }

    @Test
    public void executeShouldFailsIfCircuitIsHalfOpenedAndOperationFails() {
        // when
        final Future<?> future1 = executeWithFail("exception1");
        final Future<?> future2 = executeWithFail(null);
        waitForClosingInterval();
        final Future<?> future3 = executeWithFail("exception3");

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.failed()).isTrue();
        assertThat(future3.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception3");
    }

    @Test
    public void executeShouldSucceedsIfCircuitIsHalfOpenedAndOperationSucceeds() {
        // when
        final Future<?> future1 = executeWithFail("exception1");
        final Future<?> future2 = executeWithFail("exception2");
        waitForClosingInterval();
        final Future<?> future3 = executeWithSuccess("value after half-open");

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.succeeded()).isTrue();
        assertThat(future3.result()).isEqualTo("value after half-open");
    }

    @Test
    public void executeShouldFailsWithOriginalExceptionIfOpeningIntervalExceeds() {
        // given
        circuitBreaker = new CircuitBreaker("name", vertx, 2, 100L, 200L, clock);

        // when
        final Future<?> future1 = executeWithFail("exception1");
        waitForOpeningInterval();
        final Future<?> future2 = executeWithFail("exception2");

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception2");
    }

    private Future<String> executeWithSuccess(String result) {
        return execute(operationPromise -> operationPromise.complete(result));
    }

    private Future<String> executeWithFail(String errorMessage) {
        return execute(operationPromise -> operationPromise.fail(new RuntimeException(errorMessage)));
    }

    private Future<String> execute(Handler<Promise<String>> handler) {
        final Future<String> future = circuitBreaker.execute(handler);

        final Promise<?> promise = Promise.promise();
        future.onComplete(ar -> promise.complete());
        promise.future().toCompletionStage().toCompletableFuture().join();

        return future;
    }

    private void waitForOpeningInterval() {
        waitForInterval(150L);
    }

    private void waitForClosingInterval() {
        waitForInterval(250L);
    }

    private void waitForInterval(long timeout) {
        final Promise<?> promise = Promise.promise();
        vertx.setTimer(timeout, id -> promise.complete());
        promise.future().toCompletionStage().toCompletableFuture().join();
    }
}

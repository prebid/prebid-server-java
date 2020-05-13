package org.prebid.server.vertx;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class CircuitBreakerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private Vertx vertx;

    private Clock clock;

    private CircuitBreaker circuitBreaker;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        circuitBreaker = new CircuitBreaker("name", vertx, 1, 100L, 200L, clock);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void executeShouldSucceedsIfOperationSucceeds(TestContext context) {
        // when
        final Future<?> future = executeWithSuccess(context, "value");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("value");
    }

    @Test
    public void executeShouldFailsIfCircuitIsClosedAndOperationFails(TestContext context) {
        // when
        final Future<?> future = executeWithFail(context, "exception");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception");
    }

    @Test
    public void executeShouldFailsIfCircuitIsHalfOpenedAndOperationFailsAndClosingTimeIsNotPassedBy(
            TestContext context) {
        // when
        final Future<?> future1 = executeWithFail(context, "exception1");
        final Future<?> future2 = executeWithFail(context, null);

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");
    }

    @Test
    public void executeShouldFailsIfCircuitIsHalfOpenedAndOperationFails(TestContext context) {
        // when
        final Future<?> future1 = executeWithFail(context, "exception1");
        final Future<?> future2 = executeWithFail(context, null);
        waitForClosingInterval(context);
        final Future<?> future3 = executeWithFail(context, "exception3");

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.failed()).isTrue();
        assertThat(future3.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception3");
    }

    @Test
    public void executeShouldSucceedsIfCircuitIsHalfOpenedAndOperationSucceeds(TestContext context) {
        // when
        final Future<?> future1 = executeWithFail(context, "exception1");
        final Future<?> future2 = executeWithFail(context, "exception2");
        waitForClosingInterval(context);
        final Future<?> future3 = executeWithSuccess(context, "value after half-open");

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

        assertThat(future3.succeeded()).isTrue();
        assertThat(future3.result()).isEqualTo("value after half-open");
    }

    @Test
    public void executeShouldFailsWithOriginalExceptionIfOpeningIntervalExceeds(TestContext context) {
        // given
        circuitBreaker = new CircuitBreaker("name", vertx, 2, 100L, 200L, clock);

        // when
        final Future<?> future1 = executeWithFail(context, "exception1");
        waitForOpeningInterval(context);
        final Future<?> future2 = executeWithFail(context, "exception2");

        // then
        assertThat(future1.failed()).isTrue();
        assertThat(future1.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception1");

        assertThat(future2.failed()).isTrue();
        assertThat(future2.cause()).isInstanceOf(RuntimeException.class).hasMessage("exception2");
    }

    private Future<String> executeWithSuccess(TestContext context, String result) {
        return execute(context, operationPromise -> operationPromise.complete(result));
    }

    private Future<String> executeWithFail(TestContext context, String errorMessage) {
        return execute(context, operationPromise -> operationPromise.fail(new RuntimeException(errorMessage)));
    }

    private Future<String> execute(TestContext context, Handler<Promise<String>> handler) {
        final Future<String> future = circuitBreaker.execute(handler);

        final Async async = context.async();
        future.setHandler(ar -> async.complete());
        async.await();

        return future;
    }

    private void waitForOpeningInterval(TestContext context) {
        waitForInterval(context, 150L);
    }

    private void waitForClosingInterval(TestContext context) {
        waitForInterval(context, 250L);
    }

    private void waitForInterval(TestContext context, long timeout) {
        final Async async = context.async();
        vertx.setTimer(timeout, id -> async.complete());
        async.await();
    }
}

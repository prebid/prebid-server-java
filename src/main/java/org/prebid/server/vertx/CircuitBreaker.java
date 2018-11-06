package org.prebid.server.vertx;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.Objects;

/**
 * Wrapper over Vert.x {@link io.vertx.circuitbreaker.CircuitBreaker} with functionality
 * to reset failure counter to adjust open-circuit time frame.
 */
public class CircuitBreaker {

    private final io.vertx.circuitbreaker.CircuitBreaker breaker;
    private final long openingIntervalMs;

    private long lastFailure;

    public CircuitBreaker(String name, Vertx vertx, int openingThreshold, long openingIntervalMs,
                          long closingIntervalMs) {
        breaker = io.vertx.circuitbreaker.CircuitBreaker.create(
                Objects.requireNonNull(name),
                Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setMaxFailures(openingThreshold)
                        .setResetTimeout(closingIntervalMs));

        this.openingIntervalMs = openingIntervalMs;
    }

    /**
     * Executes the given operation with the circuit breaker control.
     */
    public <T> Future<T> execute(Handler<Future<T>> command) {
        return breaker.execute(future -> execute(command, future));
    }

    /**
     * Executes operation and handle result of it on given {@link Future}.
     */
    private <T> void execute(Handler<Future<T>> command, Future<T> future) {
        final Future<T> passedFuture = Future.future();
        command.handle(passedFuture);

        passedFuture
                .compose(response -> succeedBreaker(response, future))
                .recover(exception -> failBreaker(exception, future));
    }

    /**
     * Succeeds given {@link Future} and returns it.
     */
    private static <T> Future<T> succeedBreaker(T result, Future<T> future) {
        future.complete(result);
        return future;
    }

    /**
     * Fails given {@link Future} and returns it.
     */
    private <T> Future<T> failBreaker(Throwable exception, Future<T> future) {
        ensureToIncrementFailureCount();

        future.fail(exception);
        return future;
    }

    /**
     * Reset failure counter to adjust open-circuit time frame.
     */
    private void ensureToIncrementFailureCount() {
        final long currentTimeMillis = System.currentTimeMillis();

        if (breaker.state() == CircuitBreakerState.CLOSED && lastFailure > 0
                && currentTimeMillis - lastFailure > openingIntervalMs) {
            breaker.reset();
        }

        lastFailure = currentTimeMillis;
    }

    /**
     * Sets a {@link Handler} invoked when the circuit breaker state switches to open.
     */
    public CircuitBreaker openHandler(Handler<Void> handler) {
        breaker.openHandler(handler);
        return this;
    }

    /**
     * Sets a {@link Handler} invoked when the circuit breaker state switches to half-open.
     */
    public CircuitBreaker halfOpenHandler(Handler<Void> handler) {
        breaker.halfOpenHandler(handler);
        return this;
    }

    /**
     * Sets a {@link Handler} invoked when the circuit breaker state switches to close.
     */
    public CircuitBreaker closeHandler(Handler<Void> handler) {
        breaker.closeHandler(handler);
        return this;
    }
}

package org.prebid.server.vertx;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.Clock;
import java.util.Objects;

/**
 * Wrapper over Vert.x {@link io.vertx.circuitbreaker.CircuitBreaker} with functionality
 * to reset failure counter to adjust open-circuit time frame.
 */
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private final io.vertx.circuitbreaker.CircuitBreaker breaker;
    private final Vertx vertx;
    private final long openingIntervalMs;
    private final Clock clock;

    private volatile long lastFailureTime;

    public CircuitBreaker(String name, Vertx vertx, int openingThreshold, long openingIntervalMs,
                          long closingIntervalMs, Clock clock) {
        breaker = io.vertx.circuitbreaker.CircuitBreaker.create(
                Objects.requireNonNull(name),
                Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setMaxFailures(openingThreshold)
                        .setResetTimeout(closingIntervalMs));

        this.vertx = vertx;
        this.openingIntervalMs = openingIntervalMs;
        this.clock = Objects.requireNonNull(clock);
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
        final Future<T> ensureStateFuture = Future.future();
        vertx.executeBlocking(this::ensureState, false, ensureStateFuture);

        return ensureStateFuture
                .recover(throwable -> {
                    logger.warn("Resetting circuit breaker state failed", throwable);
                    future.fail(throwable);
                    return future;
                })
                .compose(ignored -> { // ensuring state succeeded, propagate real error
                    future.fail(exception);
                    return future;
                });
    }

    /**
     * Resets failure counter to adjust open-circuit time frame.
     * <p>
     * Note: the operations {@link io.vertx.circuitbreaker.CircuitBreaker#state()}
     * and {@link io.vertx.circuitbreaker.CircuitBreaker#reset()} can take a while,
     * so it is better to perform them on a worker thread.
     */
    private <T> void ensureState(Future<T> executeBlockingFuture) {
        final long currentTime = clock.millis();
        if (breaker.state() == CircuitBreakerState.CLOSED && lastFailureTime > 0
                && currentTime - lastFailureTime > openingIntervalMs) {
            breaker.reset();
        }

        lastFailureTime = currentTime;
        executeBlockingFuture.complete();
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

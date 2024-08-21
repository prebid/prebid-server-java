package org.prebid.server.vertx;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

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

    public CircuitBreaker(String name,
                          Vertx vertx,
                          int openingThreshold,
                          long openingIntervalMs,
                          long closingIntervalMs,
                          Clock clock) {

        breaker = io.vertx.circuitbreaker.CircuitBreaker.create(
                Objects.requireNonNull(name),
                Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setNotificationPeriod(0)
                        .setMaxFailures(openingThreshold)
                        .setResetTimeout(closingIntervalMs));

        this.vertx = vertx;
        this.openingIntervalMs = openingIntervalMs;
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Executes the given operation with the circuit breaker control.
     */
    public <T> Future<T> execute(Handler<Promise<T>> command) {
        return breaker.execute(promise -> execute(command, promise));
    }

    /**
     * Executes operation and handle result of it on given {@link Promise}.
     */
    private <T> void execute(Handler<Promise<T>> command, Promise<T> promise) {
        final Promise<T> passedPromise = Promise.promise();
        command.handle(passedPromise);

        passedPromise.future()
                .compose(response -> succeedBreaker(response, promise))
                .recover(exception -> failBreaker(exception, promise));
    }

    /**
     * Succeeds given {@link Promise} and returns corresponding {@link Future}.
     */
    private static <T> Future<T> succeedBreaker(T result, Promise<T> promise) {
        promise.complete(result);
        return promise.future();
    }

    /**
     * Fails given {@link Promise} and returns corresponding {@link Future}.
     */
    private <T> Future<T> failBreaker(Throwable exception, Promise<T> promise) {
        final Promise<T> ensureStatePromise = Promise.promise();
        vertx.executeBlocking(this::ensureState, false, ensureStatePromise);

        return ensureStatePromise.future()
                .recover(throwable -> {
                    logger.warn("Resetting circuit breaker state failed", throwable);
                    promise.fail(throwable);
                    return promise.future();
                })
                .compose(ignored -> { // ensuring state succeeded, propagate real error
                    promise.fail(exception);
                    return promise.future();
                });
    }

    /**
     * Resets failure counter to adjust open-circuit time frame.
     * <p>
     * Note: the operations {@link io.vertx.circuitbreaker.CircuitBreaker#state()}
     * and {@link io.vertx.circuitbreaker.CircuitBreaker#reset()} can take a while,
     * so it is better to perform them on a worker thread.
     */
    private <T> void ensureState(Promise<T> executeBlockingPromise) {
        final long currentTime = clock.millis();
        if (breaker.state() == CircuitBreakerState.CLOSED && lastFailureTime > 0
                && currentTime - lastFailureTime > openingIntervalMs) {
            breaker.reset();
        }

        lastFailureTime = currentTime;
        executeBlockingPromise.complete();
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

    public boolean isOpen() {
        return switch (breaker.state()) {
            case OPEN, HALF_OPEN -> true;
            case CLOSED -> false;
        };
    }
}

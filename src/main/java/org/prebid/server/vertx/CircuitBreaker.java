package org.prebid.server.vertx;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wrapper over Vert.x {@link io.vertx.circuitbreaker.CircuitBreaker} with functionality
 * to reset failure counter to adjust open-circuit time frame.
 */
public class CircuitBreaker {

    private final io.vertx.circuitbreaker.CircuitBreaker breaker;

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
    }

    /**
     * Executes the given operation with the circuit breaker control.
     */
    public <T> Future<T> execute(Supplier<Future<T>> command) {
        return breaker.execute(command);
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

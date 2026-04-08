package org.prebid.server.vertx;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.function.Supplier;

public class CircuitBreaker {

    private final io.vertx.circuitbreaker.CircuitBreaker breaker;

    public CircuitBreaker(String name,
                          Vertx vertx,
                          int openingThreshold,
                          long openingIntervalMs,
                          long closingIntervalMs) {

        breaker = io.vertx.circuitbreaker.CircuitBreaker.create(
                Objects.requireNonNull(name),
                Objects.requireNonNull(vertx),
                new CircuitBreakerOptions()
                        .setNotificationPeriod(0)
                        .setMaxFailures(openingThreshold)
                        .setFailuresRollingWindow(openingIntervalMs)
                        .setResetTimeout(closingIntervalMs));
    }

    public <T> Future<T> execute(Supplier<Future<T>> command) {
        return breaker.execute(command);
    }

    public CircuitBreaker openHandler(Handler<Void> handler) {
        breaker.openHandler(handler);
        return this;
    }

    public CircuitBreaker halfOpenHandler(Handler<Void> handler) {
        breaker.halfOpenHandler(handler);
        return this;
    }

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

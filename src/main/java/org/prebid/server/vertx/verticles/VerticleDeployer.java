package org.prebid.server.vertx.verticles;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VerticleDeployer {

    private final long timeoutMillis;
    private final Vertx vertx;

    public VerticleDeployer(Vertx vertx, long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        this.vertx = Objects.requireNonNull(vertx);
    }

    public void deploy(VerticleDefinition definition) {
        final int amount = definition.getAmount();
        if (amount <= 0) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final List<InitializableVerticle> verticles = toVerticles(definition);
        final CompositeFuture verticlesInitialization = toVerticlesInitialization(verticles)
                .onComplete(result -> latch.countDown());
        verticles.forEach(vertx::deployVerticle);

        try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                        "Action has not completed within defined timeout %d ms".formatted(timeoutMillis));
            }

            if (verticlesInitialization.failed()) {
                final Throwable cause = verticlesInitialization.cause();
                if (cause != null) {
                    throw new RuntimeException(cause);
                } else {
                    throw new RuntimeException("Action failed");
                }
            }
        } catch (
                InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for action to complete", e);
        }
    }

    private static List<InitializableVerticle> toVerticles(VerticleDefinition definition) {
        return IntStream.range(0, definition.getAmount())
                .mapToObj(i -> definition.getFactory().get())
                .toList();
    }

    private static CompositeFuture toVerticlesInitialization(List<InitializableVerticle> verticles) {
        final List<Future> verticlesInitializations = verticles.stream()
                .map(InitializableVerticle::getVerticleInitialization)
                .collect(Collectors.toCollection(ArrayList::new));

        return CompositeFuture.all(verticlesInitializations);
    }
}

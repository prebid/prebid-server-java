package org.prebid.server.vertx.verticles;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

        final AtomicReference<AsyncResult<String>> deployResult = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        vertx.deployVerticle(
                definition.getFactory(),
                new DeploymentOptions().setInstances(definition.getAmount()),
                result -> {
                    deployResult.set(result);
                    latch.countDown();
                });

        try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                        "Action has not completed within defined timeout %d ms".formatted(timeoutMillis));
            }

            final AsyncResult<String> result = deployResult.get();
            if (result.failed()) {
                throw Optional.ofNullable(result.cause())
                        .map(RuntimeException::new)
                        .orElse(new RuntimeException("Action failed"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for action to complete", e);
        }
    }
}

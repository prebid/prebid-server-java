package org.prebid.server.vertx.verticles;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class VerticleDeployer {

    private static final int TIMEOUT_MILLIS = 5000;

    private final Vertx vertx;

    public VerticleDeployer(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    public void deploy(VerticleDefinition definition) {
        final int amount = definition.getAmount();
        if (amount <= 0) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failureThrowable = new AtomicReference<>();
        final AtomicBoolean failed = new AtomicBoolean();

        final DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setInstances(amount);

        vertx.deployVerticle(definition.getFactory(), deploymentOptions, result -> {
            if (result.failed()) {
                failureThrowable.set(result.cause());
                failed.set(true);
            }

            latch.countDown();
        });

        try {
            if (!latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                        "Action has not completed within defined timeout %d ms".formatted(TIMEOUT_MILLIS));
            }

            if (failed.get()) {
                final Throwable cause = failureThrowable.get();
                if (cause != null) {
                    throw new RuntimeException(cause);
                } else {
                    throw new RuntimeException("Action failed");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for action to complete", e);
        }
    }
}

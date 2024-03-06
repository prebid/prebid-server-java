package org.prebid.server.vertx;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ContextRunner {

    private final Vertx vertx;
    private final long timeoutMs;

    public ContextRunner(Vertx vertx, long timeoutMs) {
        this.vertx = vertx;
        this.timeoutMs = timeoutMs;
    }

    public <T> void runBlocking(Handler<Promise<T>> action) {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final Promise<T> promise = Promise.promise();
        final Future<T> future = promise.future();

        future.onComplete(ignored -> completionLatch.countDown());
        vertx.runOnContext(v -> {
            try {
                action.handle(promise);
            } catch (RuntimeException e) {
                promise.fail(e);
            }
        });

        try {
            if (!completionLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                        "Action has not completed within defined timeout %d ms".formatted(timeoutMs));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for action to complete", e);
        }

        if (future.failed()) {
            throw new RuntimeException(future.cause());
        }
    }
}

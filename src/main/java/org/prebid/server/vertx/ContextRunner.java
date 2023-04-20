package org.prebid.server.vertx;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Component that manages Vertx contexts and provides interface to run arbitrary code on them.
 * <p>
 * Needed mostly to replace verticle deployment model provided by Vertx because it doesn't play nicely when using
 * Vertx in embedded mode within Spring application.
 */
public class ContextRunner {

    private static final Logger logger = LoggerFactory.getLogger(ContextRunner.class);

    private final long timeoutMs;

    private final Context serviceContext;

    public ContextRunner(Vertx vertx, long timeoutMs) {
        this.timeoutMs = timeoutMs;

        this.serviceContext = vertx.getOrCreateContext();
    }

    /**
     * Runs provided action on a dedicated service context.
     */
    public <T> void runOnServiceContext(Handler<Promise<T>> action) {
        runOnContext(() -> serviceContext, action);
    }

    private <T> void runOnContext(Supplier<Context> contextFactory, Handler<Promise<T>> action) {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final AtomicBoolean actionFailed = new AtomicBoolean(false);
        final Context context = contextFactory.get();

        final Promise<T> promise = Promise.promise();
        promise.future().onComplete(ar -> {
            if (ar.failed()) {
                logger.fatal("Fatal error occurred while running action on Vertx context", ar.cause());
                actionFailed.compareAndSet(false, true);
            }
            completionLatch.countDown();
        });

        context.runOnContext(v -> {
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
            } else if (actionFailed.get()) {
                throw new RuntimeException("Action failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for action to complete", e);
        }
    }
}

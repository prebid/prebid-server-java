package org.prebid.server.vertx;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Objects;
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

    private final Vertx vertx;
    private final long timeoutMs;

    private final Context serviceContext;

    public ContextRunner(Vertx vertx, long timeoutMs) {
        this.vertx = Objects.requireNonNull(vertx);
        this.timeoutMs = timeoutMs;

        this.serviceContext = vertx.getOrCreateContext();
    }

    /**
     * Runs provided action specified number of times each in a new context. This method is handy for
     * running several instances of {@link io.vertx.core.http.HttpServer} on different event loop threads.
     */
    public <T> void runOnNewContext(int times, Handler<Future<T>> action) {
        runOnContext(vertx::getOrCreateContext, times, action);
    }

    /**
     * Runs provided action on a dedicated service context.
     */
    public <T> void runOnServiceContext(Handler<Future<T>> action) {
        runOnContext(() -> serviceContext, 1, action);
    }

    private <T> void runOnContext(Supplier<Context> contextFactory, int times, Handler<Future<T>> action) {
        final CountDownLatch completionLatch = new CountDownLatch(times);
        final AtomicBoolean actionFailed = new AtomicBoolean(false);

        for (int i = 0; i < times; i++) {
            final Context context = contextFactory.get();
            context.runOnContext(v -> action.handle(Future.<T>future().setHandler(ar -> {
                if (ar.failed()) {
                    logger.fatal("Fatal error occurred while running action on Vertx context", ar.cause());
                    actionFailed.compareAndSet(false, true);
                }
                completionLatch.countDown();
            })));
        }

        try {
            if (!completionLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                        String.format("Action has not completed within defined timeout %d ms", timeoutMs));
            } else if (actionFailed.get()) {
                throw new RuntimeException("Action failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for action to complete", e);
        }
    }
}

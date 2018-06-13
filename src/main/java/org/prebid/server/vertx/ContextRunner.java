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

public class ContextRunner {

    private static final Logger logger = LoggerFactory.getLogger(ContextRunner.class);

    private final Vertx vertx;

    private final Context serviceContext;

    public ContextRunner(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);

        this.serviceContext = vertx.getOrCreateContext();
    }

    public void runOnNewContext(int times, Handler<Future<Void>> action, long timeoutMs) {
        runOnContext(vertx::getOrCreateContext, times, action, timeoutMs);
    }

    public <T> void runOnServiceContext(Handler<Future<T>> action, long timeoutMs) {
        runOnContext(() -> serviceContext, 1, action, timeoutMs);
    }

    private <T> void runOnContext(Supplier<Context> contextFactory, int times, Handler<Future<T>> action,
                                  long timeoutMs) {
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

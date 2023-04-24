package org.prebid.server.vertx.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Base class for server Verticles, exists for making asynchronous verticles initialization synchronous,
 * so that server bootstrap will crash if verticle can't init. Every child class should call either
 * {@link #signalInitializationFailure(Throwable)} or {@link #signalInitializationFailure(Throwable)} at
 * the end of initialization, which should be done in {@link #init(Vertx, Context)} method
 */
public abstract class InitializableVerticle extends AbstractVerticle {

    private final Promise<Void> initializationPromise = Promise.promise();

    protected void signalInitializationSuccess() {
        initializationPromise.tryComplete();
    }

    protected void signalInitializationFailure(Throwable cause) {
        initializationPromise.tryFail(cause);
    }

    public Future<Void> getInitializationMarker() {
        return initializationPromise.future();
    }

    @Override
    public abstract void init(Vertx vertx, Context context);
}

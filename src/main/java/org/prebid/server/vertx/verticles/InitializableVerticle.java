package org.prebid.server.vertx.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Base class for pbs Verticles, exists for making asynchronous verticles initialization synchronous,
 * so that server bootstrap will crash if verticle can't init. Every child class should do initialization
 * in {@link #initialize(Vertx, Context)} method
 */
public abstract class InitializableVerticle extends AbstractVerticle {

    private final Promise<Void> initializationPromise = Promise.promise();

    public Future<Void> getInitializationMarker() {
        return initializationPromise.future();
    }

    public abstract Future<Void> initialize(Vertx vertx, Context context);

    @Override
    public void init(Vertx vertx, Context context) {
        initialize(vertx, context)
                .onSuccess(initializationPromise::tryComplete)
                .onFailure(initializationPromise::tryFail);
    }
}

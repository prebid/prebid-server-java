package org.prebid.server.vertx.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * Base class for server Verticles, is made for making asynchronous verticles initialization synchronous,
 * so that server bootstrap will crash if verticle can't init.
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
}

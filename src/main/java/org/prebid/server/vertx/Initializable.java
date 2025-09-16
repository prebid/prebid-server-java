package org.prebid.server.vertx;

import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * Denotes components requiring initialization after they have been created.
 * <p>
 * Initialization is expected to be performed on Vert.x {@link io.vertx.core.Context} (event loop) thread, see
 * {@link io.vertx.core.Vertx#runOnContext(Handler)} and/or {@link io.vertx.core.Context#runOnContext(Handler)}.
 */
@FunctionalInterface
public interface Initializable {

    void initialize(Promise<Void> initializePromise);
}

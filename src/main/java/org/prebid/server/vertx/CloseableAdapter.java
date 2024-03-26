package org.prebid.server.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

/**
 * {@link CloseableAdapter} implementation used for auto-closing instances of
 * {@link com.codahale.metrics.ScheduledReporter}.
 */
public class CloseableAdapter implements io.vertx.core.Closeable {

    private final Closeable adaptee;

    public CloseableAdapter(Closeable adaptee) {
        this.adaptee = Objects.requireNonNull(adaptee);
    }

    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
        try {
            adaptee.close();
            completionHandler.handle(Future.succeededFuture());
        } catch (IOException e) {
            completionHandler.handle(Future.failedFuture(e));
        }
    }
}

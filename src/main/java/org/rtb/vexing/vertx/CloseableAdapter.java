package org.rtb.vexing.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

public class CloseableAdapter<T extends Closeable> implements io.vertx.core.Closeable {

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

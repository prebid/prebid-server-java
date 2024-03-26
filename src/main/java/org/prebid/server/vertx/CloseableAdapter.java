package org.prebid.server.vertx;

import io.vertx.core.Promise;

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
    public void close(Promise<Void> completion) {
        try {
            adaptee.close();
            completion.tryComplete();
        } catch (IOException e) {
            completion.tryFail(e);
        }
    }
}

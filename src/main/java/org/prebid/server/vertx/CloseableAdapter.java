package org.prebid.server.vertx;

import io.vertx.core.Completable;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

/**
 * {@link CloseableAdapter} implementation used for auto-closing instances of
 * {@link com.codahale.metrics.ScheduledReporter}.
 */
public class CloseableAdapter implements io.vertx.core.Closeable {

    private final Closeable closeable;

    public CloseableAdapter(Closeable closeable) {
        this.closeable = Objects.requireNonNull(closeable);
    }

    @Override
    public void close(Completable<Void> completable) {
        try {
            closeable.close();
            completable.succeed();
        } catch (IOException e) {
            completable.fail(e);
        }
    }
}

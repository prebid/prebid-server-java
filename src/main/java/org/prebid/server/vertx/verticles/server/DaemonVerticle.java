package org.prebid.server.vertx.verticles.server;

import com.codahale.metrics.ScheduledReporter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.vertx.CloseableAdapter;
import org.prebid.server.vertx.Initializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DaemonVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(DaemonVerticle.class);

    private final List<Initializable> initializables;
    private final List<Closeable> closeables;

    public DaemonVerticle(List<Initializable> initializables, List<ScheduledReporter> reporters) {
        this.initializables = ListUtils.emptyIfNull(initializables);
        this.closeables = ListUtils.emptyIfNull(reporters).stream()
                .<Closeable>map(CloseableAdapter::new)
                .toList();
    }

    @Override
    public void start(Promise<Void> startPromise) {
        startPromise.handle(all(initializables, initializable -> initializable::initialize));
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        stopPromise.handle(all(closeables, closeable -> closeable::close));
    }

    private static <E> Future<Void> all(Collection<E> entries,
                                        Function<E, Consumer<Promise<Void>>> entryToPromiseConsumerMapper) {

        final List<Future<Void>> entriesFutures = new ArrayList<>();

        for (E entry : entries) {
            final Promise<Void> entryPromise = Promise.promise();
            entriesFutures.add(entryPromise.future());

            entryToPromiseConsumerMapper.apply(entry).accept(entryPromise);
        }

        return Future.all(entriesFutures)
                .onSuccess(r -> logger.info(
                        "Successfully started {} instance on thread: {}",
                        DaemonVerticle.class.getSimpleName(),
                        Thread.currentThread().getName()))
                .mapEmpty();
    }
}

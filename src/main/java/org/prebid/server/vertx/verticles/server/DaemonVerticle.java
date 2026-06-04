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

import java.util.Collection;
import java.util.List;
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
        all(initializables, Initializable::initialize, true).onComplete(startPromise);
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        all(closeables, closeable -> Future.future(closeable::close), false).onComplete(stopPromise);
    }

    private static <E> Future<Void> all(
            Collection<E> entries,
            Function<E, Future<Void>> entryToFutureMapper,
            boolean start) {

        return Future.all(entries.stream().map(entryToFutureMapper).toList())
                .onSuccess(r -> logger.info(
                        "Successfully {} {} instance on thread: {}",
                        start ? "started" : "stopped",
                        DaemonVerticle.class.getSimpleName(),
                        Thread.currentThread().getName()))
                .mapEmpty();
    }

}

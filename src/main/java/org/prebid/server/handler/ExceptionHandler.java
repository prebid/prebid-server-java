package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.metric.Metrics;

import java.util.Objects;

public class ExceptionHandler implements Handler<Throwable> {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    private Metrics metrics;

    public ExceptionHandler(Metrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    public static ExceptionHandler create(Metrics metrics) {
        return new ExceptionHandler(metrics);
    }

    @Override
    public void handle(Throwable throwable) {
        logger.warn("Error while establishing HTTP connection", throwable);
        metrics.updateConnectionAcceptErrors();
    }
}

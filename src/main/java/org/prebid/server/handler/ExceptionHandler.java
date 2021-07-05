package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.metric.Metrics;

import java.util.Objects;

public class ExceptionHandler implements Handler<Throwable> {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    private final Metrics metrics;

    public ExceptionHandler(Metrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    public static ExceptionHandler create(Metrics metrics) {
        return new ExceptionHandler(metrics);
    }

    @Override
    public void handle(Throwable exception) {
        logger.warn("Generic error handler: {0}, cause: {1}",
                errorMessageFrom(exception), errorMessageFrom(exception.getCause()));
        metrics.updateConnectionAcceptErrors();
    }

    private static String errorMessageFrom(Throwable exception) {
        final String message = exception != null ? exception.getMessage() : null;
        return StringUtils.defaultIfEmpty(message, "''");
    }
}

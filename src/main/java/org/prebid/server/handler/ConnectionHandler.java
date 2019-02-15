package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.metric.Metrics;

import java.util.Objects;

public class ConnectionHandler implements Handler<HttpConnection> {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionHandler.class);

    private Metrics metrics;

    public ConnectionHandler(Metrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    public static ConnectionHandler create(Metrics metrics) {
        return new ConnectionHandler(metrics);
    }

    @Override
    public void handle(HttpConnection connection) {
        connection.exceptionHandler(this::handleException);
    }

    private void handleException(Throwable throwable) {
        logger.warn("HTTP connection error occurred", throwable);
        metrics.updateConnectionCloseErrors();
    }
}

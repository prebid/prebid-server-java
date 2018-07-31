package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import org.prebid.server.metric.Metrics;

import java.util.Objects;

public class ConnectionHandler implements Handler<HttpConnection> {

    private Metrics metrics;

    private ConnectionHandler(Metrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    public static ConnectionHandler create(Metrics metrics) {
        return new ConnectionHandler(metrics);
    }

    @Override
    public void handle(HttpConnection connection) {
        metrics.updateActiveConnectionsMetrics(true);

        connection.closeHandler(ignored -> metrics.updateActiveConnectionsMetrics(false));
    }
}

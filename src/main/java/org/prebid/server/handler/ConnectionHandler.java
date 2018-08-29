package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.metric.Metrics;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionHandler implements Handler<HttpConnection> {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionHandler.class);

    private Metrics metrics;
    private int inboundConnectionsLimit;
    private AtomicInteger counter;

    private ConnectionHandler(Metrics metrics, int inboundConnectionsLimit, AtomicInteger counter) {
        this.metrics = Objects.requireNonNull(metrics);
        this.inboundConnectionsLimit = inboundConnectionsLimit;
        this.counter = counter;
    }

    public static ConnectionHandler create(Metrics metrics, Vertx vertx, int inboundConnectionsLimit,
                                           long inboundConnectionsCounterResetPeriod) {
        final boolean manageInboundConnections = inboundConnectionsLimit > 0;
        final AtomicInteger counter = manageInboundConnections ? new AtomicInteger() : null;

        // for rare cases when connection.closeHandler() is not invoked
        // we have to reset the counter periodically
        if (manageInboundConnections) {
            vertx.setPeriodic(inboundConnectionsCounterResetPeriod, ignored -> {
                int oldValue = counter.getAndSet(0);
                logger.info("Inbound connections counter is dropped to 0, was: {0}", oldValue);
            });
        }

        return new ConnectionHandler(metrics, inboundConnectionsLimit, counter);
    }

    @Override
    public void handle(HttpConnection connection) {
        metrics.updateActiveConnectionsMetrics(true);

        connection.closeHandler(ignored -> {
            metrics.updateActiveConnectionsMetrics(false);

            if (inboundConnectionsLimit > 0) {
                counter.decrementAndGet();
            }
        });

        if (inboundConnectionsLimit > 0) {
            int connectionsCount = counter.incrementAndGet();

            if (connectionsCount > inboundConnectionsLimit) {
                logger.warn("Inbound connections limit has been exceeded: {0}", connectionsCount);
                connection.close();
            }
        }
    }
}

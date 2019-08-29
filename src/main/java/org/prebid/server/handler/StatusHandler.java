package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.health.HealthChecker;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StatusHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(StatusHandler.class);

    private final List<HealthChecker> healthCheckers;

    public StatusHandler(List<HealthChecker> healthCheckers) {
        this.healthCheckers = Objects.requireNonNull(healthCheckers);
    }

    @Override
    public void handle(RoutingContext context) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }

        if (CollectionUtils.isEmpty(healthCheckers)) {
            context.response()
                    .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                    .end();
        } else {
            context.response()
                    .end(Json.encode(new TreeMap<>(healthCheckers.stream()
                            .collect(Collectors.toMap(HealthChecker::name, HealthChecker::status)))));
        }
    }
}

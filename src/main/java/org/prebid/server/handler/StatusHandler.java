package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.health.HealthChecker;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StatusHandler implements Handler<RoutingContext> {

    private final List<HealthChecker> healthCheckers;

    public StatusHandler(List<HealthChecker> healthCheckers) {
        this.healthCheckers = Objects.requireNonNull(healthCheckers);
    }

    @Override
    public void handle(RoutingContext context) {
        if (CollectionUtils.isEmpty(healthCheckers)) {
            context.response().setStatusCode(HttpResponseStatus.NO_CONTENT.code()).end();
        } else {
            context.response().end(Json.encode(new TreeMap<>(healthCheckers.stream()
                    .collect(Collectors.toMap(HealthChecker::name, HealthChecker::status)))));
        }
    }
}

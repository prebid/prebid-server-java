package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.health.model.StatusResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
            context.response().end(Json.encode(statusByHealthChecker()));
        }
    }

    private Map<String, StatusResponse> statusByHealthChecker() {
        final Map<String, StatusResponse> responseMap = new TreeMap<>();

        healthCheckers.forEach(
                healthChecker -> responseMap.put(healthChecker.name(), healthChecker.status()));
        return responseMap;
    }
}

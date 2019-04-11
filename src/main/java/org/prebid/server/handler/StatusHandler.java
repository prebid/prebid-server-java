package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.health.HealthChecker;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class StatusHandler implements Handler<RoutingContext> {

    private final String statusResponse;
    private final List<HealthChecker> healthCheckers;

    public StatusHandler(String statusResponse, List<HealthChecker> healthCheckers) {
        this.statusResponse = statusResponse;
        this.healthCheckers = healthCheckers;
    }

    @Override
    public void handle(RoutingContext context) {
        // Today, the app always considers itself ready to serve requests.
        if (StringUtils.isEmpty(statusResponse)) {
            context.response().setStatusCode(HttpResponseStatus.NO_CONTENT.code()).end();
        } else {
            context.response().end(resolveResponseString());
        }
    }

    private String resolveResponseString() {
        final Map<String, Object> response = new TreeMap<>();
        response.put("application", statusResponse);

        healthCheckers.forEach(
                healthChecker -> response.put(
                        healthChecker.getCheckName(),
                        healthChecker.getLastStatus()));

        return Json.encode(response);
    }
}

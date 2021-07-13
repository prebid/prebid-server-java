package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StatusHandler implements Handler<RoutingContext> {

    private final List<HealthChecker> healthCheckers;
    private final JacksonMapper mapper;

    public StatusHandler(List<HealthChecker> healthCheckers, JacksonMapper mapper) {
        this.healthCheckers = Objects.requireNonNull(healthCheckers);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Consumer<HttpServerResponse> responseConsumer;

        if (CollectionUtils.isEmpty(healthCheckers)) {
            responseConsumer = response -> response
                    .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                    .end();
        } else {
            responseConsumer = response -> response
                    .end(mapper.encode(new TreeMap<>(healthCheckers.stream()
                            .collect(Collectors.toMap(HealthChecker::name, HealthChecker::status)))));
        }

        HttpUtil.executeSafely(routingContext, Endpoint.status, responseConsumer);
    }
}

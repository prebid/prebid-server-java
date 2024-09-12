package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.health.model.StatusResponse;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StatusHandler implements ApplicationResource {

    private final List<HealthChecker> healthCheckers;
    private final JacksonMapper mapper;

    public StatusHandler(List<HealthChecker> healthCheckers, JacksonMapper mapper) {
        this.healthCheckers = Objects.requireNonNull(healthCheckers);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.GET, Endpoint.status.value()));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (CollectionUtils.isEmpty(healthCheckers)) {
            HttpUtil.executeSafely(routingContext, Endpoint.status,
                    response -> response
                            .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                            .end());
        } else {
            final TreeMap<String, StatusResponse> nameToStatus = new TreeMap<>(healthCheckers.stream()
                    .collect(Collectors.toMap(HealthChecker::name, HealthChecker::status)));

            HttpUtil.executeSafely(routingContext, Endpoint.status,
                    response -> response
                            .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                            .end(mapper.encodeToString(nameToStatus)));
        }
    }
}

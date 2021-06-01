package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.execution.HttpResponseSender;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.json.JacksonMapper;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StatusHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(StatusHandler.class);

    private final List<HealthChecker> healthCheckers;
    private final JacksonMapper mapper;

    public StatusHandler(List<HealthChecker> healthCheckers, JacksonMapper mapper) {
        this.healthCheckers = Objects.requireNonNull(healthCheckers);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpResponseStatus status;
        final String body;
        if (CollectionUtils.isEmpty(healthCheckers)) {
            status = HttpResponseStatus.NO_CONTENT;
            body = null;
        } else {
            status = HttpResponseStatus.OK;
            body = mapper.encode(new TreeMap<>(healthCheckers.stream()
                    .collect(Collectors.toMap(HealthChecker::name, HealthChecker::status))));
        }
        HttpResponseSender.from(routingContext, logger)
                .status(status)
                .body(body)
                .send();
    }
}

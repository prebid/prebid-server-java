package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.log.LoggerControlKnob;
import org.prebid.server.util.HttpUtil;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

public class LoggerControlKnobHandler implements Handler<RoutingContext> {

    private static final String LEVEL_PARAMETER = "level";
    private static final String DURATION_PARAMETER = "duration";

    private static final Set<String> ALLOWED_LEVELS = Set.of("error", "warn", "info", "debug");

    private final long maxDurationMs;
    private final LoggerControlKnob loggerControlKnob;
    private final String endpoint;

    public LoggerControlKnobHandler(long maxDurationMs, LoggerControlKnob loggerControlKnob, String endpoint) {
        this.maxDurationMs = maxDurationMs;
        this.loggerControlKnob = Objects.requireNonNull(loggerControlKnob);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            final MultiMap parameters = routingContext.request().params();
            loggerControlKnob.changeLogLevel(readLevel(parameters), readDuration(parameters));

            HttpUtil.executeSafely(routingContext, endpoint,
                    HttpServerResponse::end);
        } catch (InvalidRequestException e) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                            .end(e.getMessage()));
        }
    }

    private String readLevel(MultiMap parameters) {
        final String level = parameters.get(LEVEL_PARAMETER);

        if (level == null) {
            throw new InvalidRequestException(String.format("Missing required parameter '%s'", LEVEL_PARAMETER));
        }

        if (!ALLOWED_LEVELS.contains(level.toLowerCase())) {
            throw new InvalidRequestException(String.format(
                    "Invalid '%s' parameter value, allowed values '%s'", LEVEL_PARAMETER, ALLOWED_LEVELS));
        }

        return level;
    }

    private Duration readDuration(MultiMap parameters) {
        final Integer duration = getIntParameter(DURATION_PARAMETER, parameters);

        if (duration == null) {
            throw new InvalidRequestException(String.format("Missing required parameter '%s'", DURATION_PARAMETER));
        }

        if (duration < 1 || duration > maxDurationMs) {
            throw new InvalidRequestException(String.format(
                    "Parameter '%s' must be between %d and %d", DURATION_PARAMETER, 0, maxDurationMs));
        }

        return Duration.ofMillis(duration);
    }

    private Integer getIntParameter(String parameterName, MultiMap parameters) {
        final String value = parameters.get(parameterName);
        try {
            return value != null ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(String.format("Invalid '%s' parameter value", parameterName));
        }
    }
}

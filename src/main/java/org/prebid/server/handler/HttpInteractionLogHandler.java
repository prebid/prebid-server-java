package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.model.HttpLogSpec;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Objects;

public class HttpInteractionLogHandler implements Handler<RoutingContext> {

    private static final String ENDPOINT_PARAMETER = "endpoint";
    private static final String STATUS_CODE_PARAMETER = "statusCode";
    private static final String ACCOUNT_PARAMETER = "account";
    private static final String LIMIT_PARAMETER = "limit";

    private final int maxLimit;
    private final HttpInteractionLogger httpInteractionLogger;
    private final String endpoint;

    public HttpInteractionLogHandler(int maxLimit, HttpInteractionLogger httpInteractionLogger, String endpoint) {
        this.maxLimit = maxLimit;
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final MultiMap parameters = routingContext.request().params();

        try {
            httpInteractionLogger.setSpec(HttpLogSpec.of(
                    readEndpoint(parameters),
                    readStatusCode(parameters),
                    readAccount(parameters),
                    readLimit(parameters)));

            HttpUtil.executeSafely(routingContext, endpoint,
                    HttpServerResponse::end);
        } catch (InvalidRequestException e) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                            .end(e.getMessage()));
        }
    }

    private HttpLogSpec.Endpoint readEndpoint(MultiMap parameters) {
        final String endpoint = parameters.get(ENDPOINT_PARAMETER);
        try {
            return endpoint != null ? HttpLogSpec.Endpoint.valueOf(endpoint) : null;
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(String.format(
                    "Invalid '%s' parameter value, allowed values '%s'",
                    ENDPOINT_PARAMETER,
                    Arrays.toString(HttpLogSpec.Endpoint.values())));
        }
    }

    private Integer readStatusCode(MultiMap parameters) {
        final Integer statusCode = getIntParameter(STATUS_CODE_PARAMETER, parameters);

        if (statusCode != null && (statusCode < 200 || statusCode > 500)) {
            throw new InvalidRequestException(String.format(
                    "Parameter '%s' must be between %d and %d", STATUS_CODE_PARAMETER, 200, 500));
        }

        return statusCode;
    }

    private String readAccount(MultiMap parameters) {
        return parameters.get(ACCOUNT_PARAMETER);
    }

    private int readLimit(MultiMap parameters) {
        final Integer limit = getIntParameter(LIMIT_PARAMETER, parameters);

        if (limit == null) {
            throw new InvalidRequestException(String.format("Missing required parameter '%s'", LIMIT_PARAMETER));
        }

        if (limit < 1 || limit > maxLimit) {
            throw new InvalidRequestException(String.format(
                    "Parameter '%s' must be between %d and %d", LIMIT_PARAMETER, 0, maxLimit));
        }

        return limit;
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

package org.prebid.server.vertx.verticles.server.application;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;

import java.util.Arrays;
import java.util.List;

public class DeprecatedResource implements ApplicationResource {

    private final String message;

    private final List<HttpEndpoint> endpoints;

    public DeprecatedResource(String message, HttpEndpoint... endpoints) {
        this.message = message;
        this.endpoints = Arrays.asList(endpoints);
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return endpoints;
    }

    @Override
    public void handle(RoutingContext event) {
        event.response()
                .setStatusCode(HttpResponseStatus.GONE.code())
                .end(StringUtils.defaultString(message));
    }
}

package org.prebid.server.handler.admin;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.vertx.verticles.server.admin.AdminResource;

import java.util.Objects;

public class AdminResourceWrapper implements AdminResource {

    private final String path;
    private final Handler<RoutingContext> handler;
    private final boolean isOnApplicationPort;
    private final boolean isSecured;

    public AdminResourceWrapper(String path,
                                boolean isOnApplicationPort,
                                boolean isProtected,
                                Handler<RoutingContext> handler) {

        this.path = Objects.requireNonNull(path);
        this.isOnApplicationPort = isOnApplicationPort;
        this.isSecured = isProtected;
        this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public String path() {
        return path;
    }

    public boolean isOnApplicationPort() {
        return isOnApplicationPort;
    }

    @Override
    public boolean isSecured() {
        return isSecured;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        handler.handle(routingContext);
    }
}

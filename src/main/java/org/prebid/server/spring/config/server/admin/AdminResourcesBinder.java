package org.prebid.server.spring.config.server.admin;

import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import org.prebid.server.vertx.verticles.server.admin.AdminResource;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdminResourcesBinder {

    private final Map<String, String> credentials;
    private final List<AdminResource> resources;

    public AdminResourcesBinder(Map<String, String> credentials, List<AdminResource> resources) {
        this.credentials = credentials;
        this.resources = Objects.requireNonNull(resources);
    }

    public void bind(Router router) {
        for (AdminResource resource : resources) {
            router
                    .route(resource.path())
                    .handler(resource.isSecured() ? securedAuthHandler() : PassNextHandler.INSTANCE)
                    .handler(resource);
        }
    }

    private AuthenticationHandler securedAuthHandler() {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials for admin endpoint is empty.");
        }

        return BasicAuthHandler.create(new AdminServerAuthProvider(credentials));
    }

    private static class PassNextHandler implements Handler<RoutingContext> {

        private static final Handler<RoutingContext> INSTANCE = new PassNextHandler();

        @Override
        public void handle(RoutingContext event) {
            event.next();
        }
    }
}

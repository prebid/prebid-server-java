package org.prebid.server.spring.config.server.admin;

import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
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
        resources.forEach(resource -> bindResource(router, resource));
    }

    private void bindResource(Router router, AdminResource resource) {
        final String path = resource.path();

        if (resource.isSecured()) {
            if (credentials == null) {
                throw new IllegalArgumentException("Credentials for admin endpoint is empty.");
            }

            final AuthProvider authProvider = new AdminServerAuthProvider(credentials);
            router.route(path).handler(BasicAuthHandler.create(authProvider)).handler(resource);
        } else {
            router.route(path).handler(resource);
        }
    }
}

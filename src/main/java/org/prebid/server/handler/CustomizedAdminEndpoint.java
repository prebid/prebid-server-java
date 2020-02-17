package org.prebid.server.handler;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BasicAuthHandler;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

public class CustomizedAdminEndpoint {

    private final Handler<RoutingContext> handler;
    private final boolean isOnApplicationPort;
    private final boolean isProtected;
    private final String path;
    private Map<String, String> adminEndpointCredentials;

    public CustomizedAdminEndpoint(String path, Handler<RoutingContext> handler, boolean isOnApplicationPort,
                                   boolean isProtected) {
        this.path = path;
        this.handler = handler;
        this.isOnApplicationPort = isOnApplicationPort;
        this.isProtected = isProtected;
    }

    public CustomizedAdminEndpoint(String path, Handler<RoutingContext> handler, boolean isOnApplicationPort,
                                   boolean isProtected, Map<String, String> adminEndpointCredentials) {
        this.path = path;
        this.handler = handler;
        this.isOnApplicationPort = isOnApplicationPort;
        this.isProtected = isProtected;
        this.adminEndpointCredentials = adminEndpointCredentials;
    }

    public boolean isOnApplicationPort() {
        return isOnApplicationPort;
    }

    public void router(Router router) {
        if (isProtected) {
            routeSecureToHandler(router);
        } else {
            routeToHandler(router);
        }
    }

    private void routeToHandler(Router router) {
        router.route(path).handler(handler);
    }

    private void routeSecureToHandler(Router router) {
        if (adminEndpointCredentials == null) {
            throw new IllegalArgumentException("Credentials for admin endpoint is empty.");
        }
        final AuthProvider authProvider = createAuthProvider(adminEndpointCredentials);
        router.route(path).handler(BasicAuthHandler.create(authProvider)).handler(handler);
    }

    private AuthProvider createAuthProvider(Map<String, String> credentials) {
        return (authInfo, resultHandler) -> {
            if (MapUtils.isEmpty(credentials)) {
                resultHandler.handle(Future.failedFuture("Credentials not set in configuration."));
                return;
            }
            final String requestUsername = authInfo.getString("username");
            final String requestPassword = StringUtils.chomp(authInfo.getString("password"));
            final String storedPassword = credentials.get(requestUsername);
            if (StringUtils.isNotBlank(requestPassword) && Objects.equals(storedPassword, requestPassword)) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                resultHandler.handle(Future.failedFuture("No such user, or password incorrect."));
            }
        };
    }
}

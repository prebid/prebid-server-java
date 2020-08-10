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

    private final String path;
    private final Handler<RoutingContext> handler;
    private final boolean isOnApplicationPort;
    private final boolean isProtected;
    private Map<String, String> credentials;

    public CustomizedAdminEndpoint(String path, Handler<RoutingContext> handler, boolean isOnApplicationPort,
                                   boolean isProtected) {
        this.path = Objects.requireNonNull(path);
        this.handler = Objects.requireNonNull(handler);
        this.isOnApplicationPort = isOnApplicationPort;
        this.isProtected = isProtected;
    }

    public CustomizedAdminEndpoint withCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
        return this;
    }

    public boolean isOnApplicationPort() {
        return isOnApplicationPort;
    }

    public void router(Router router) {
        if (isProtected) {
            routeToHandlerWithCredentials(router);
        } else {
            routeToHandler(router);
        }
    }

    private void routeToHandlerWithCredentials(Router router) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials for admin endpoint is empty.");
        }

        final AuthProvider authProvider = createAuthProvider(credentials);
        router.route(path).handler(BasicAuthHandler.create(authProvider)).handler(handler);
    }

    private void routeToHandler(Router router) {
        router.route(path).handler(handler);
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

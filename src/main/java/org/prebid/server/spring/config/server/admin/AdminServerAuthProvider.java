package org.prebid.server.spring.config.server.admin;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class AdminServerAuthProvider implements AuthProvider {

    private final Map<String, String> credentials;

    public AdminServerAuthProvider(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        if (MapUtils.isEmpty(credentials)) {
            resultHandler.handle(Future.failedFuture("Credentials not set in configuration."));
            return;
        }

        final String requestUsername = authInfo.getString("username");
        final String requestPassword = StringUtils.chomp(authInfo.getString("password"));

        final String storedPassword = credentials.get(requestUsername);
        if (StringUtils.isNotBlank(requestPassword) && StringUtils.equals(storedPassword, requestPassword)) {
            resultHandler.handle(Future.succeededFuture());
        } else {
            resultHandler.handle(Future.failedFuture("No such user, or password incorrect."));
        }
    }
}

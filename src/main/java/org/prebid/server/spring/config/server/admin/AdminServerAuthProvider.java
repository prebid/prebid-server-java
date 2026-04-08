package org.prebid.server.spring.config.server.admin;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authentication.Credentials;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class AdminServerAuthProvider implements AuthenticationProvider {

    private final Map<String, String> credentials;

    public AdminServerAuthProvider(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    @Override
    public Future<User> authenticate(Credentials userCredentials) {
        if (MapUtils.isEmpty(credentials)) {
            return Future.failedFuture("Credentials not set in configuration.");
        }

        final JsonObject principal = userCredentials.toJson();
        final String requestUsername = principal.getString("username");
        final String requestPassword = StringUtils.chomp(principal.getString("password"));
        final String storedPassword = credentials.get(requestUsername);

        return StringUtils.isNotBlank(requestPassword) && StringUtils.equals(storedPassword, requestPassword)
                ? Future.succeededFuture()
                : Future.failedFuture("Password does not match.");
    }
}

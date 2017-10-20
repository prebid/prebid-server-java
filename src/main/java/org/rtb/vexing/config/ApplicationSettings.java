package org.rtb.vexing.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.rtb.vexing.config.model.Account;

import java.util.Objects;
import java.util.Optional;

public interface ApplicationSettings {

    enum Type {
        filecache, postgres
    }

    Optional<Account> getAccountById(String accountId);

    Optional<String> getAdUnitConfigById(String adUnitConfigId);

    static ApplicationSettings create(Vertx vertx, JsonObject config) {
        Objects.requireNonNull(config);

        switch (Type.valueOf(config.getString("datacache.type"))) {
            case filecache:
                return FileApplicationSettings.create(vertx.fileSystem(), config.getString("datacache.filename"));
            default:
                throw new IllegalStateException("Should never happen");
        }
    }
}

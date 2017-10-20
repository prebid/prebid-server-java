package org.rtb.vexing.settings;

import io.vertx.core.Vertx;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.settings.model.Account;

import java.util.Objects;
import java.util.Optional;

public interface ApplicationSettings {

    enum Type {
        filecache, postgres
    }

    Optional<Account> getAccountById(String accountId);

    Optional<String> getAdUnitConfigById(String adUnitConfigId);

    static ApplicationSettings create(Vertx vertx, ApplicationConfig config) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(config);

        switch (Type.valueOf(config.getString("datacache.type"))) {
            case filecache:
                return FileApplicationSettings.create(vertx.fileSystem(), config.getString("datacache.filename"));
            default:
                throw new IllegalStateException("Should never happen");
        }
    }
}

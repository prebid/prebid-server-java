package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.settings.model.Account;

import java.util.Objects;

public interface ApplicationSettings {

    enum Type {
        filecache, postgres
    }

    Future<Account> getAccountById(String accountId);

    Future<String> getAdUnitConfigById(String adUnitConfigId);

    static Future<? extends ApplicationSettings> create(Vertx vertx, ApplicationConfig config) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(config);

        switch (Type.valueOf(config.getString("datacache.type"))) {
            case filecache:
                return FileApplicationSettings.create(vertx.fileSystem(), config.getString("datacache.filename"));
            case postgres:
                final Future<JdbcApplicationSettings> jdbcApplicationSettings = JdbcApplicationSettings.create(vertx,
                        postgresUrl(config), "org.postgresql.Driver", config.getInteger("datacache.max_pool_size"));
                return jdbcApplicationSettings.map(settings -> new CachingApplicationSettings(settings,
                        config.getInteger("datacache.ttl_seconds"), config.getInteger("datacache.cache_size")));
            default:
                throw new IllegalStateException("Should never happen");
        }
    }

    static String postgresUrl(ApplicationConfig config) {
        return String.format("jdbc:postgresql://%s/%s?user=%s&password=%s",
                config.getString("datacache.host"),
                config.getString("datacache.database"),
                config.getString("datacache.username"),
                config.getString("datacache.password"));
    }
}

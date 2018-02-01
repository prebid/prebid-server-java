package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.settings.model.Account;
import org.rtb.vexing.settings.model.SourceType;

import java.util.Objects;

public interface ApplicationSettings {

    Future<Account> getAccountById(String accountId);

    Future<String> getAdUnitConfigById(String adUnitConfigId);

    static Future<? extends ApplicationSettings> create(Vertx vertx, ApplicationConfig config) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(config);

        final SourceType sourceType = SourceType.valueOf(config.getString("datacache.type"));

        // Jdbc config for both, datacache and storedRequest was moved to StoredRequest config part. Now it is important
        // to check that in case of db source, datacache has the same sql provider as StoredRequest to avoid
        // misconfiguration.
        if (Objects.equals(SourceType.postgres, sourceType) || Objects.equals(SourceType.mysql, sourceType)) {
            if (sourceType != SourceType.valueOf(config.getString("stored_requests.type"))) {
                throw new IllegalStateException("Sql provider types are different for datacache and storedRequest");
            }
        }

        switch (sourceType) {
            case filesystem:
                return FileApplicationSettings.create(vertx.fileSystem(), config.getString("datacache.filename"));
            case postgres:
                final Future<JdbcApplicationSettings> postgresJdbcApplicationSettings = JdbcApplicationSettings
                        .create(vertx, jdbcUrl(config, "jdbc:postgresql:"), "org.postgresql.Driver",
                                config.getInteger("stored_requests.max_pool_size"));
                return postgresJdbcApplicationSettings.map(settings -> new CachingApplicationSettings(settings,
                        config.getInteger("datacache.ttl_seconds"), config.getInteger("datacache.cache_size")));
            case mysql:
                final Future<JdbcApplicationSettings> mysqlJdbcApplicationSettings = JdbcApplicationSettings
                        .create(vertx, jdbcUrl(config, "jdbc:mysql:"), "com.mysql.cj.jdbc.Driver",
                                config.getInteger("stored_requests.max_pool_size"));
                return mysqlJdbcApplicationSettings.map(settings -> new CachingApplicationSettings(settings,
                        config.getInteger("datacache.ttl_seconds"), config.getInteger("datacache.cache_size")));
            default:
                throw new IllegalStateException("Should never happen");
        }
    }

    static String jdbcUrl(ApplicationConfig config, String protocol) {
        return String.format("%s//%s/%s?user=%s&password=%s&useSSL=false",
                protocol,
                // db configuration was moved to StoredRequest config part
                config.getString("stored_requests.host"),
                config.getString("stored_requests.dbname"),
                config.getString("stored_requests.user"),
                config.getString("stored_requests.password"));
    }
}

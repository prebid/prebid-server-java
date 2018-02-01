package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.settings.model.SourceType;
import org.rtb.vexing.settings.model.StoredRequestResult;

import java.util.Objects;
import java.util.Set;

/**
 * Interface for executing stored requests fetching from sources.
 */
public interface StoredRequestFetcher {

    /**
     * Fetches stored requests by ids.
     */
    Future<StoredRequestResult> getStoredRequestsById(Set<String> ids);

    /**
     * Creates appropriate {@link FileStoredRequestFetcher} or JdbcStoredRequestFetcher
     * {@link JdbcStoredRequestFetcher} based on configuration in ApplicationConfig {@link ApplicationConfig}
     */
    static Future<? extends StoredRequestFetcher> create(Vertx vertx, ApplicationConfig config) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(config);

        switch (SourceType.valueOf(config.getString("stored_requests.type"))) {
            case filesystem:
                return FileStoredRequestFetcher.create(config.getString("stored_requests.configpath"),
                        vertx.fileSystem());
            case postgres:
                return JdbcStoredRequestFetcher.create(vertx, jdbcUrl(config, "jdbc:postgresql:"),
                        "org.postgresql.Driver", config.getInteger("stored_requests.max_pool_size"),
                        config.getString("stored_requests.query"));
            case mysql:
                final Future<JdbcStoredRequestFetcher> jdbcStoredRequestFetcher = JdbcStoredRequestFetcher
                        .create(vertx, jdbcUrl(config, "jdbc:mysql:"), "com.mysql.cj.jdbc.Driver", config.getInteger(
                                "stored_requests.max_pool_size"), config.getString("stored_requests.query"));
                // wrap with cache if ttl and cacheSize are defined in config
                final Integer ttl = config.getInteger("stored_requests.in_memory_cache.ttl_seconds");
                final Integer cacheSize = config.getInteger("stored_requests.in_memory_cache.cache_size");
                if (ttl != null && cacheSize != null) {
                    return jdbcStoredRequestFetcher.map(storedRequestFetcher ->
                            new CachingStoredRequestFetcher(storedRequestFetcher, ttl, cacheSize));
                }
                return jdbcStoredRequestFetcher;
            default:
                throw new IllegalStateException("Should never happen");
        }
    }

    /**
     * Creates String representation of jdbc configuration from ApplicationConfig{@link ApplicationConfig}
     */
    static String jdbcUrl(ApplicationConfig config, String protocol) {
        return String.format("%s//%s/%s?user=%s&password=%s&useSSL=false",
                protocol,
                config.getString("stored_requests.host"),
                config.getString("stored_requests.dbname"),
                config.getString("stored_requests.user"),
                config.getString("stored_requests.password"));
    }
}

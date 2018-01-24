package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.settings.model.StoredRequestResult;

import java.util.Objects;
import java.util.Set;

/**
 * Interface for executing stored requests fetching from sources.
 */
public interface StoredRequestFetcher {

    enum Type {
        filesystem, postgres
    }

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

        switch (Type.valueOf(config.getString("stored_requests.type"))) {
            case filesystem:
                return FileStoredRequestFetcher.create(config.getString("stored_requests.configpath"),
                        vertx.fileSystem());
            case postgres:
                return JdbcStoredRequestFetcher.create(vertx, postgresUrl(config), "org.postgresql.Driver",
                        config.getInteger("stored_requests.max_pool_size"), config.getString("stored_requests.query"));
            default:
                throw new IllegalStateException("Should never happen");
        }
    }

    /**
     * Creates String representation of jdbc configuration from ApplicationConfig{@link ApplicationConfig}
     */
    static String postgresUrl(ApplicationConfig config) {
        return String.format("jdbc:postgresql://%s/%s?user=%s&password=%s",
                config.getString("stored_requests.host"),
                config.getString("stored_requests.dbname"),
                config.getString("stored_requests.user"),
                config.getString("stored_requests.password"));
    }
}

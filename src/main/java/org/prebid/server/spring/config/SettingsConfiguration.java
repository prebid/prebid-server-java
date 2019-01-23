package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.CompositeApplicationSettings;
import org.prebid.server.settings.FileApplicationSettings;
import org.prebid.server.settings.HttpApplicationSettings;
import org.prebid.server.settings.JdbcApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.settings.service.HttpPeriodicRefreshService;
import org.prebid.server.settings.service.JdbcPeriodicRefreshService;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.jdbc.BasicJdbcClient;
import org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient;
import org.prebid.server.vertx.jdbc.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SettingsConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "settings.filesystem",
            name = {"settings-filename", "stored-requests-dir", "stored-imps-dir"})
    static class FileSettingsConfiguration {

        @Bean
        FileApplicationSettings fileApplicationSettings(
                @Value("${settings.filesystem.settings-filename}") String settingsFileName,
                @Value("${settings.filesystem.stored-requests-dir}") String storedRequestsDir,
                @Value("${settings.filesystem.stored-imps-dir}") String storedImpsDir,
                FileSystem fileSystem) {

            return FileApplicationSettings.create(fileSystem, settingsFileName, storedRequestsDir, storedImpsDir);
        }
    }

    @Configuration
    @ConditionalOnExpression("'${settings.database.type}' == 'postgres' or '${settings.database.type}' == 'mysql'")
    static class DatabaseSettingsConfiguration {

        @Bean
        JdbcApplicationSettings jdbcApplicationSettings(
                @Value("${settings.database.stored-requests-query}") String storedRequestsQuery,
                @Value("${settings.database.amp-stored-requests-query}") String ampStoredRequestsQuery,
                JdbcClient jdbcClient) {

            return new JdbcApplicationSettings(jdbcClient, storedRequestsQuery, ampStoredRequestsQuery);
        }

        @Bean
        @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "false",
                matchIfMissing = true)
        BasicJdbcClient basicJdbcClient(
                Vertx vertx, JDBCClient vertxJdbcClient, Metrics metrics, Clock clock, ContextRunner contextRunner) {

            return createBasicJdbcClient(vertx, vertxJdbcClient, metrics, clock, contextRunner);
        }

        @Bean
        @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "true")
        CircuitBreakerSecuredJdbcClient circuitBreakerSecuredJdbcClient(
                Vertx vertx, JDBCClient vertxJdbcClient, Metrics metrics, Clock clock, ContextRunner contextRunner,
                @Value("${settings.database.circuit-breaker.opening-threshold}") int openingThreshold,
                @Value("${settings.database.circuit-breaker.opening-interval-ms}") long openingIntervalMs,
                @Value("${settings.database.circuit-breaker.closing-interval-ms}") long closingIntervalMs) {

            final JdbcClient jdbcClient = createBasicJdbcClient(vertx, vertxJdbcClient, metrics, clock, contextRunner);
            return new CircuitBreakerSecuredJdbcClient(vertx, jdbcClient, metrics, openingThreshold, openingIntervalMs,
                    closingIntervalMs);
        }

        private static BasicJdbcClient createBasicJdbcClient(
                Vertx vertx, JDBCClient vertxJdbcClient, Metrics metrics, Clock clock, ContextRunner contextRunner) {
            final BasicJdbcClient basicJdbcClient = new BasicJdbcClient(vertx, vertxJdbcClient, metrics, clock);

            contextRunner.runOnServiceContext(
                    future -> basicJdbcClient.initialize().compose(ignored -> future.complete(), future));

            return basicJdbcClient;
        }

        @Bean
        JDBCClient vertxJdbcClient(Vertx vertx, StoredRequestsDatabaseProperties storedRequestsDatabaseProperties) {
            final String jdbcUrl = String.format("%s//%s:%d/%s?%s",
                    storedRequestsDatabaseProperties.getType().jdbcUrlPrefix,
                    storedRequestsDatabaseProperties.getHost(),
                    storedRequestsDatabaseProperties.getPort(),
                    storedRequestsDatabaseProperties.getDbname(),
                    storedRequestsDatabaseProperties.getType().jdbcUrlSuffix);

            return JDBCClient.createShared(vertx, new JsonObject()
                    .put("url", jdbcUrl)
                    .put("user", storedRequestsDatabaseProperties.getUser())
                    .put("password", storedRequestsDatabaseProperties.getPassword())
                    .put("driver_class", storedRequestsDatabaseProperties.getType().jdbcDriver)
                    .put("initial_pool_size", storedRequestsDatabaseProperties.getPoolSize())
                    .put("min_pool_size", storedRequestsDatabaseProperties.getPoolSize())
                    .put("max_pool_size", storedRequestsDatabaseProperties.getPoolSize()));
        }

        @Component
        @ConfigurationProperties(prefix = "settings.database")
        @ConditionalOnExpression("'${settings.database.type}' == 'postgres' or '${settings.database.type}' == 'mysql'")
        @Validated
        @Data
        @NoArgsConstructor
        private static class StoredRequestsDatabaseProperties {

            @NotNull
            private DbType type;
            @NotNull
            @Min(1)
            private Integer poolSize;
            @NotBlank
            private String host;
            @NotNull
            private Integer port;
            @NotBlank
            private String dbname;
            @NotBlank
            private String user;
            @NotBlank
            private String password;
        }

        @AllArgsConstructor
        private enum DbType {
            postgres("org.postgresql.Driver", "jdbc:postgresql:", "ssl=false&socketTimeout=1&tcpKeepAlive=true"),
            mysql("com.mysql.cj.jdbc.Driver", "jdbc:mysql:", "useSSL=false&socketTimeout=1000&tcpKeepAlive=true");

            private final String jdbcDriver;
            private final String jdbcUrlPrefix;
            private final String jdbcUrlSuffix;
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.http", name = {"endpoint", "amp-endpoint"})
    static class HttpSettingsConfiguration {

        @Bean
        HttpApplicationSettings httpApplicationSettings(
                HttpClient httpClient,
                @Value("${settings.http.endpoint}") String endpoint,
                @Value("${settings.http.amp-endpoint}") String ampEndpoint) {

            return new HttpApplicationSettings(httpClient, endpoint, ampEndpoint);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.in-memory-cache.http-update",
            name = {"endpoint", "amp-endpoint", "refresh-rate", "timeout"})
    static class HttpPeriodicRefreshServiceConfiguration {

        @Value("${settings.in-memory-cache.http-update.refresh-rate}")
        long refreshPeriod;

        @Value("${settings.in-memory-cache.http-update.timeout}")
        long timeout;

        @Autowired
        Vertx vertx;

        @Autowired
        HttpClient httpClient;

        @Bean
        public HttpPeriodicRefreshService httpPeriodicRefreshService(
                @Value("${settings.in-memory-cache.http-update.endpoint}") String endpoint,
                SettingsCache settingsCache) {

            return new HttpPeriodicRefreshService(settingsCache, endpoint, refreshPeriod, timeout, vertx, httpClient);
        }

        @Bean
        public HttpPeriodicRefreshService ampHttpPeriodicRefreshService(
                @Value("${settings.in-memory-cache.http-update.amp-endpoint}") String ampEndpoint,
                SettingsCache ampSettingsCache) {

            return new HttpPeriodicRefreshService(ampSettingsCache, ampEndpoint, refreshPeriod, timeout, vertx,
                    httpClient);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.in-memory-cache.jdbc-update",
            name = {"refresh-rate", "timeout", "init-query", "update-query", "amp-init-query", "amp-update-query"})
    static class JdbcPeriodicRefreshServiceConfiguration {

        @Value("${settings.in-memory-cache.jdbc-update.refresh-rate}")
        long refreshPeriod;

        @Value("${settings.in-memory-cache.jdbc-update.timeout}")
        long timeout;

        @Autowired
        Vertx vertx;

        @Autowired
        JdbcClient jdbcClient;

        @Autowired
        TimeoutFactory timeoutFactory;

        @Bean
        public JdbcPeriodicRefreshService jdbcPeriodicRefreshService(
                SettingsCache settingsCache,
                @Value("${settings.in-memory-cache.jdbc-update.init-query}") String initQuery,
                @Value("${settings.in-memory-cache.jdbc-update.update-query}") String updateQuery) {

            return new JdbcPeriodicRefreshService(settingsCache, vertx, jdbcClient, refreshPeriod,
                    initQuery, updateQuery, timeoutFactory, timeout);
        }

        @Bean
        public JdbcPeriodicRefreshService ampJdbcPeriodicRefreshService(
                SettingsCache settingsCache,
                @Value("${settings.in-memory-cache.jdbc-update.amp-init-query}") String ampInitQuery,
                @Value("${settings.in-memory-cache.jdbc-update.amp-update-query}") String ampUpdateQuery) {

            return new JdbcPeriodicRefreshService(settingsCache, vertx, jdbcClient, refreshPeriod,
                    ampInitQuery, ampUpdateQuery, timeoutFactory, timeout);
        }
    }

    /**
     * This configuration defines a collection of application settings fetchers and its ordering.
     */
    @Configuration
    static class CompositeSettingsConfiguration {

        @Bean
        CompositeApplicationSettings compositeApplicationSettings(
                @Autowired(required = false) FileApplicationSettings fileApplicationSettings,
                @Autowired(required = false) JdbcApplicationSettings jdbcApplicationSettings,
                @Autowired(required = false) HttpApplicationSettings httpApplicationSettings) {

            final List<ApplicationSettings> applicationSettingsList =
                    Stream.of(fileApplicationSettings,
                            jdbcApplicationSettings,
                            httpApplicationSettings)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

            return new CompositeApplicationSettings(applicationSettingsList);
        }
    }

    @Configuration
    static class CachingSettingsConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
        CachingApplicationSettings cachingApplicationSettings(
                CompositeApplicationSettings compositeApplicationSettings,
                ApplicationSettingsCacheProperties cacheProperties,
                @Qualifier("settingsCache") SettingsCache cache,
                @Qualifier("ampSettingsCache") SettingsCache ampCache) {

            return new CachingApplicationSettings(
                    compositeApplicationSettings,
                    cache,
                    ampCache,
                    cacheProperties.getTtlSeconds(),
                    cacheProperties.getCacheSize());
        }
    }

    @Configuration
    static class ApplicationSettingsConfiguration {

        @Bean
        ApplicationSettings applicationSettings(
                @Autowired(required = false) CachingApplicationSettings cachingApplicationSettings,
                @Autowired(required = false) CompositeApplicationSettings compositeApplicationSettings) {
            return ObjectUtils.firstNonNull(cachingApplicationSettings, compositeApplicationSettings);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
    static class CacheConfiguration {

        @Bean
        @Qualifier("settingsCache")
        SettingsCache settingsCache(ApplicationSettingsCacheProperties cacheProperties) {
            return new SettingsCache(cacheProperties.getTtlSeconds(), cacheProperties.getCacheSize());
        }

        @Bean
        @Qualifier("ampSettingsCache")
        SettingsCache ampSettingsCache(ApplicationSettingsCacheProperties cacheProperties) {
            return new SettingsCache(cacheProperties.getTtlSeconds(), cacheProperties.getCacheSize());
        }
    }

    @Component
    @ConfigurationProperties(prefix = "settings.in-memory-cache")
    @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
    @Validated
    @Data
    @NoArgsConstructor
    private static class ApplicationSettingsCacheProperties {

        @NotNull
        @Min(1)
        private Integer ttlSeconds;
        @NotNull
        @Min(1)
        private Integer cacheSize;
    }
}

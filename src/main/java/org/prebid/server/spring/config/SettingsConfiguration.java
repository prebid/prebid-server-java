package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.handler.SettingsCacheNotificationHandler;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.CompositeApplicationSettings;
import org.prebid.server.settings.FileApplicationSettings;
import org.prebid.server.settings.HttpApplicationSettings;
import org.prebid.server.settings.JdbcApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.vertx.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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
        JdbcClient jdbcClient(Vertx vertx, JDBCClient vertxJdbcClient) {
            return new JdbcClient(vertx, vertxJdbcClient);
        }

        @Bean
        JDBCClient vertxJdbcClient(Vertx vertx, StoredRequestsDatabaseProperties storedRequestsDatabaseProperties) {
            final String jdbcUrl = String.format("%s//%s:%d/%s?useSSL=false",
                    storedRequestsDatabaseProperties.getType().jdbcUrlPrefix,
                    storedRequestsDatabaseProperties.getHost(),
                    storedRequestsDatabaseProperties.getPort(),
                    storedRequestsDatabaseProperties.getDbname());

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
            postgres("jdbc:postgresql:", "org.postgresql.Driver"),
            mysql("jdbc:mysql:", "com.mysql.cj.jdbc.Driver");

            private final String jdbcUrlPrefix;
            private final String jdbcDriver;
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

    @Configuration
    @ConditionalOnProperty(prefix = "settings", name = "cache-events-api", havingValue = "true")
    public static class CacheNotificationConfiguration {

        @Bean
        SettingsCacheNotificationHandler cacheNotificationHandler(SettingsCache settingsCache) {
            return new SettingsCacheNotificationHandler(settingsCache);
        }

        @Bean
        SettingsCacheNotificationHandler ampCacheNotificationHandler(SettingsCache ampSettingsCache) {
            return new SettingsCacheNotificationHandler(ampSettingsCache);
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        CacheNotificationVerticle cacheNotificationVerticle(
                @Value("${admin.port}") int port,
                Vertx vertx,
                SettingsCacheNotificationHandler cacheNotificationHandler,
                SettingsCacheNotificationHandler ampCacheNotificationHandler,
                BodyHandler bodyHandler) {

            return new CacheNotificationVerticle(vertx, port, cacheNotificationHandler, ampCacheNotificationHandler,
                    bodyHandler);
        }
    }
}

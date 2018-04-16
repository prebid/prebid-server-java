package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.FileApplicationSettings;
import org.prebid.server.settings.JdbcApplicationSettings;
import org.prebid.server.vertx.JdbcClient;
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

public class SettingsConfiguration {

    @Configuration
    @ConditionalOnProperty(name = "settings.type", havingValue = "filesystem")
    public static class FileSettingsConfiguration {

        @Bean(name = "applicationSettings")
        ApplicationSettings fileApplicationSettings(
                @Value("${settings.settings-filename}") String settingsFileName,
                @Value("${settings.stored-requests-dir}") String storedRequestsDir,
                FileSystem fileSystem) {

            return FileApplicationSettings.create(fileSystem, settingsFileName, storedRequestsDir);
        }
    }

    @Configuration
    @ConditionalOnExpression("'${settings.type}' == 'postgres' or '${settings.type}' == 'mysql'")
    public static class DatabaseSettingsConfiguration {

        @Bean(name = "applicationSettings")
        @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
        ApplicationSettings cachingApplicationSettings(
                JdbcApplicationSettings jdbcApplicationSettings,
                ApplicationSettingsCacheProperties applicationSettingsCacheProperties) {

            return new CachingApplicationSettings(
                    jdbcApplicationSettings,
                    applicationSettingsCacheProperties.getTtlSeconds(),
                    applicationSettingsCacheProperties.getCacheSize());
        }

        @Bean
        JdbcApplicationSettings jdbcApplicationSettings(
                @Value("${settings.stored-requests-query}") String storedRequestsQuery,
                @Value("${settings.amp-stored-requests-query}") String ampStoreRequestsQuery,
                JdbcClient jdbcClient) {

            return new JdbcApplicationSettings(jdbcClient, storedRequestsQuery, ampStoreRequestsQuery);
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
    }

    @AllArgsConstructor
    private enum DbType {
        postgres("jdbc:postgresql:", "org.postgresql.Driver"),
        mysql("jdbc:mysql:", "com.mysql.cj.jdbc.Driver");

        private final String jdbcUrlPrefix;
        private final String jdbcDriver;
    }

    @Component
    @ConfigurationProperties(prefix = "settings")
    @ConditionalOnExpression("'${settings.type}' == 'postgres' or '${settings.type}' == 'mysql'")
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

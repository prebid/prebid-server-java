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
import org.prebid.server.settings.CachingStoredRequestFetcher;
import org.prebid.server.settings.FileApplicationSettings;
import org.prebid.server.settings.FileStoredRequestFetcher;
import org.prebid.server.settings.JdbcApplicationSettings;
import org.prebid.server.settings.JdbcStoredRequestFetcher;
import org.prebid.server.settings.StoredRequestFetcher;
import org.prebid.server.vertx.JdbcClient;
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

@Configuration
public class SettingsConfiguration {

    @Bean(name = "applicationSettings")
    @ConditionalOnProperty(name = "datacache.type", havingValue = "filesystem")
    ApplicationSettings fileApplicationSettings(
            @Value("${datacache.filename}") String filename,
            FileSystem fileSystem) {

        return FileApplicationSettings.create(fileSystem, filename);
    }

    @Bean(name = "applicationSettings")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnExpression("'${datacache.type}' == 'postgres' or '${datacache.type}' == 'mysql'")
    ApplicationSettings cachingApplicationSettings(
            JdbcApplicationSettings jdbcApplicationSettings,
            ApplicationSettingsCacheProperties applicationSettingsCacheProperties) {

        return new CachingApplicationSettings(
                jdbcApplicationSettings,
                applicationSettingsCacheProperties.getTtlSeconds(),
                applicationSettingsCacheProperties.getCacheSize());
    }

    @Bean
    @ConditionalOnExpression("('${datacache.type}' == 'postgres' or '${datacache.type}' == 'mysql')"
            + " and '${datacache.type}' == '${stored-requests.type}'")
    JdbcApplicationSettings jdbcApplicationSettings(JdbcClient jdbcClient) {
        return new JdbcApplicationSettings(jdbcClient);
    }

    @Bean(name = "storedRequestFetcher")
    @ConditionalOnProperty(name = "stored-requests.type", havingValue = "filesystem")
    StoredRequestFetcher fileStoredRequestFetcher(
            @Value("${stored-requests.configpath}") String configPath,
            FileSystem fileSystem) {

        return FileStoredRequestFetcher.create(configPath, fileSystem);
    }

    @Bean(name = "storedRequestFetcher")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(prefix = "stored-requests.in-memory-cache", name = {"ttl-seconds", "cache-size"})
    @ConditionalOnExpression("'${stored-requests.type}' == 'postgres' or '${stored-requests.type}' == 'mysql'")
    StoredRequestFetcher cachingStoredRequestFetcher(
            JdbcStoredRequestFetcher jdbcStoredRequestFetcher,
            StoredRequestsCacheProperties storedRequestsCacheProperties) {

        return new CachingStoredRequestFetcher(
                jdbcStoredRequestFetcher,
                storedRequestsCacheProperties.getTtlSeconds(),
                storedRequestsCacheProperties.getCacheSize());
    }

    @Bean
    @ConditionalOnExpression("'${stored-requests.type}' == 'postgres' or '${stored-requests.type}' == 'mysql'")
    JdbcStoredRequestFetcher jdbcStoredRequestFetcher(
            @Value("${stored-requests.query}") String query,
            @Value("${stored-requests.amp-query}") String ampQuery,
            JdbcClient jdbcClient) {

        return new JdbcStoredRequestFetcher(jdbcClient, query, ampQuery);
    }

    @Bean
    @ConditionalOnExpression("'${datacache.type}' == 'postgres' or '${datacache.type}' == 'mysql'"
            + " or '${stored-requests.type}' == 'postgres' or '${stored-requests.type}' == 'mysql'")
    JdbcClient jdbcClient(Vertx vertx, JDBCClient vertxJdbcClient) {
        return new JdbcClient(vertx, vertxJdbcClient);
    }

    @Bean
    @ConditionalOnExpression("'${datacache.type}' == 'postgres' or '${datacache.type}' == 'mysql'"
            + " or '${stored-requests.type}' == 'postgres' or '${stored-requests.type}' == 'mysql'")
    JDBCClient vertxJdbcClient(Vertx vertx, StoredRequestsDatabaseProperties storedRequestsDatabaseProperties) {
        final String jdbcUrl = String.format("%s//%s/%s?useSSL=false",
                storedRequestsDatabaseProperties.getType().jdbcUrlPrefix,
                storedRequestsDatabaseProperties.getHost(),
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

    @AllArgsConstructor
    private enum DbType {
        postgres("jdbc:postgresql:", "org.postgresql.Driver"),
        mysql("jdbc:mysql:", "com.mysql.cj.jdbc.Driver");

        private final String jdbcUrlPrefix;
        private final String jdbcDriver;
    }

    @Component
    @ConfigurationProperties(prefix = "stored-requests")
    @ConditionalOnExpression("'${stored-requests.type}' == 'postgres' or '${stored-requests.type}' == 'mysql'")
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
        @NotBlank
        private String dbname;
        @NotBlank
        private String user;
        @NotBlank
        private String password;
    }

    @Component
    @ConfigurationProperties(prefix = "stored-requests.in-memory-cache")
    @ConditionalOnProperty(prefix = "stored-requests.in-memory-cache", name = {"ttl-seconds", "cache-size"})
    @Validated
    @Data
    @NoArgsConstructor
    private static class StoredRequestsCacheProperties {

        @NotNull
        @Min(1)
        private Integer ttlSeconds;
        @NotNull
        @Min(1)
        private Integer cacheSize;
    }

    @Component
    @ConfigurationProperties(prefix = "datacache")
    @ConditionalOnExpression("'${datacache.type}' == 'postgres' or '${datacache.type}' == 'mysql'")
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

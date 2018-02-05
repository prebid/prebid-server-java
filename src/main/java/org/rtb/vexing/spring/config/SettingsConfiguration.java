package org.rtb.vexing.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.settings.CachingApplicationSettings;
import org.rtb.vexing.settings.CachingStoredRequestFetcher;
import org.rtb.vexing.settings.FileApplicationSettings;
import org.rtb.vexing.settings.FileStoredRequestFetcher;
import org.rtb.vexing.settings.JdbcApplicationSettings;
import org.rtb.vexing.settings.JdbcStoredRequestFetcher;
import org.rtb.vexing.settings.StoredRequestFetcher;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

import java.util.Objects;

@Configuration
public class SettingsConfiguration {

    @Lookup
    ApplicationSettings applicationSettings() {
        return null;
    }

    @Lookup
    ApplicationSettings jdbcApplicationSettings() {
        return null;
    }

    @Bean(name = "applicationSettings")
    @ConditionalOnProperty(name = "datacache.type", havingValue = "filesystem")
    ApplicationSettings fileApplicationSettings(
            @Value("${datacache.filename}") String filename,
            FileSystem fileSystem) {

        return FileApplicationSettings.create(fileSystem, filename);
    }

    @Bean(name = "jdbcApplicationSettings")
    @ConditionalOnProperty(name = "datacache.type", havingValue = "postgres")
    JdbcApplicationSettings postgresApplicationSettings(
            @Value("${stored-requests.max-pool-size}") int maxPoolSize,
            Vertx vertx,
            StoredRequestProperties storedRequestProperties) {

        return JdbcApplicationSettings.create(vertx,
                JdbcApplicationSettings.jdbcUrl(storedRequestProperties, "jdbc:postgresql:"), "org.postgresql.Driver",
                maxPoolSize);
    }

    @Bean(name = "jdbcApplicationSettings")
    @ConditionalOnProperty(name = "datacache.type", havingValue = "mysql")
    JdbcApplicationSettings mysqlApplicationSettings(
            @Value("${stored-requests.max-pool-size}") int maxPoolSize,
            Vertx vertx,
            StoredRequestProperties storedRequestProperties) {

        return JdbcApplicationSettings.create(vertx,
                JdbcApplicationSettings.jdbcUrl(storedRequestProperties, "jdbc:mysql:"), "com.mysql.cj.jdbc.Driver",
                maxPoolSize);
    }

    @Bean(name = "applicationSettings")
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnExpression("'${datacache.type}' != 'filesystem'")
    CachingApplicationSettings cachingApplicationSettings(DataCacheProperties dataCacheProperties) {
        return new CachingApplicationSettings(jdbcApplicationSettings(),
                Objects.requireNonNull(dataCacheProperties.getTtlSeconds()),
                Objects.requireNonNull(dataCacheProperties.getCacheSize()));
    }

    @Lookup
    StoredRequestFetcher storedRequestFetcher() {
        return null;
    }

    @Lookup
    StoredRequestFetcher jdbcStoredRequestFetcher() {
        return null;
    }

    @Bean(name = "storedRequestFetcher")
    @ConditionalOnProperty(name = "stored-requests.type", havingValue = "filesystem")
    StoredRequestFetcher fileStoredRequestFetcher(
            @Value("${stored-requests.configpath}") String configPath,
            FileSystem fileSystem) {

        return FileStoredRequestFetcher.create(configPath, fileSystem);
    }

    @Bean(name = "jdbcStoredRequestFetcher")
    @ConditionalOnProperty(name = "stored-requests.type", havingValue = "postgres")
    JdbcStoredRequestFetcher postgresStoredRequestFetcher(
            @Value("${stored-requests.max-pool-size}") int maxPoolSize,
            @Value("${stored-requests.query}") String query,
            Vertx vertx,
            StoredRequestProperties storedRequestProperties) {

        return JdbcStoredRequestFetcher.create(vertx,
                JdbcApplicationSettings.jdbcUrl(storedRequestProperties, "jdbc:postgresql:"), "org.postgresql.Driver",
                maxPoolSize, query);
    }

    @Bean(name = "jdbcStoredRequestFetcher")
    @ConditionalOnProperty(name = "stored-requests.type", havingValue = "mysql")
    JdbcStoredRequestFetcher mysqlStoredRequestFetcher(
            @Value("${stored-requests.max-pool-size}") int maxPoolSize,
            @Value("${stored-requests.query}") String query,
            Vertx vertx,
            StoredRequestProperties storedRequestProperties) {

        return JdbcStoredRequestFetcher.create(vertx,
                JdbcStoredRequestFetcher.jdbcUrl(storedRequestProperties, "jdbc:mysql:"), "com.mysql.cj.jdbc.Driver",
                maxPoolSize, query);
    }

    @Bean(name = "storedRequestFetcher")
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnExpression("'${stored-requests.type}' != 'filesystem'"
            + " and '${stored-requests.in-memory-cache.ttl-seconds}' != null"
            + " and '${stored-requests.in-memory-cache.cache-size}' != null")
    CachingStoredRequestFetcher cachingStoredRequestFetcher(
            @Value("${stored-requests.in-memory-cache.ttl-seconds}") int ttlSeconds,
            @Value("${stored-requests.in-memory-cache.cache-size}") int cacheSize) {

        return new CachingStoredRequestFetcher(jdbcStoredRequestFetcher(), ttlSeconds, cacheSize);
    }

    @Bean
    DataCacheProperties datacacheProperties() {
        return new DataCacheProperties();
    }

    @Bean
    StoredRequestProperties storedRequestProperties() {
        return new StoredRequestProperties();
    }
}

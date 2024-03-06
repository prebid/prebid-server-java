package org.prebid.server.spring.config.database;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import org.prebid.server.metric.Metrics;
import org.prebid.server.spring.config.database.model.ConnectionPoolSettings;
import org.prebid.server.spring.config.database.model.DatabaseAddress;
import org.prebid.server.spring.config.database.properties.DatabaseConfigurationProperties;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.jdbc.BasicJdbcClient;
import org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient;
import org.prebid.server.vertx.jdbc.JdbcClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;

@Configuration
@ConditionalOnExpression("'${settings.database.type}' == 'postgres' or '${settings.database.type}' == 'mysql'")
public class DatabaseConfiguration {

    @Bean
    @ConditionalOnProperty(name = "settings.database.type", havingValue = "postgres")
    DatabaseUrlFactory postgresUrlFactory() {
        return "jdbc:postgresql://%s:%d/%s?ssl=false&socketTimeout=1&tcpKeepAlive=true"::formatted;
    }

    @Bean
    @ConditionalOnProperty(name = "settings.database.type", havingValue = "mysql")
    DatabaseUrlFactory mySqlUrlFactory() {
        return "jdbc:mysql://%s:%d/%s?useSSL=false&socketTimeout=1000&tcpKeepAlive=true"::formatted;
    }

    @Bean
    @ConditionalOnProperty(name = "settings.database.provider-class", havingValue = "hikari")
    ConnectionPoolConfigurationFactory hikariConfigurationFactory() {
        return (url, connectionPoolSettings) -> new JsonObject()
                .put("jdbcUrl", url + "&allowPublicKeyRetrieval=true")
                .put("username", connectionPoolSettings.getUser())
                .put("password", connectionPoolSettings.getPassword())
                .put("minimumIdle", connectionPoolSettings.getPoolSize())
                .put("maximumPoolSize", connectionPoolSettings.getPoolSize())
                .put("provider_class", "io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider");
    }

    @Bean
    @ConditionalOnProperty(name = "settings.database.provider-class", havingValue = "c3p0")
    ConnectionPoolConfigurationFactory c3p0ConfigurationFactory() {
        return (url, connectionPoolSettings) -> new JsonObject()
                .put("url", url)
                .put("user", connectionPoolSettings.getUser())
                .put("password", connectionPoolSettings.getPassword())
                .put("initial_pool_size", connectionPoolSettings.getPoolSize())
                .put("min_pool_size", connectionPoolSettings.getPoolSize())
                .put("max_pool_size", connectionPoolSettings.getPoolSize())
                .put("provider_class", "io.vertx.ext.jdbc.spi.impl.C3P0DataSourceProvider");
    }

    @Bean
    DatabaseAddress databaseAddress(DatabaseConfigurationProperties databaseConfigurationProperties) {
        return DatabaseAddress.of(
                databaseConfigurationProperties.getHost(),
                databaseConfigurationProperties.getPort(),
                databaseConfigurationProperties.getDbname());
    }

    @Bean
    ConnectionPoolSettings connectionPoolSettings(DatabaseConfigurationProperties databaseConfigurationProperties) {
        return ConnectionPoolSettings.of(
                databaseConfigurationProperties.getPoolSize(),
                databaseConfigurationProperties.getUser(),
                databaseConfigurationProperties.getPassword(),
                databaseConfigurationProperties.getType());
    }

    @Bean
    JDBCClient vertxJdbcClient(Vertx vertx,
                               DatabaseAddress databaseAddress,
                               ConnectionPoolSettings connectionPoolSettings,
                               DatabaseUrlFactory urlFactory,
                               ConnectionPoolConfigurationFactory configurationFactory) {

        final String databaseUrl = urlFactory.createUrl(
                databaseAddress.getHost(), databaseAddress.getPort(), databaseAddress.getDatabaseName());

        final JsonObject connectionPoolConfigurationProperties = configurationFactory.create(
                databaseUrl, connectionPoolSettings);
        final JsonObject databaseConfigurationProperties = new JsonObject()
                .put("driver_class", connectionPoolSettings.getDatabaseType().jdbcDriver);
        databaseConfigurationProperties.mergeIn(connectionPoolConfigurationProperties);

        return JDBCClient.createShared(vertx, databaseConfigurationProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    BasicJdbcClient basicJdbcClient(
            Vertx vertx, JDBCClient vertxJdbcClient, Metrics metrics, Clock clock, ContextRunner contextRunner) {

        return createBasicJdbcClient(vertx, vertxJdbcClient, metrics, clock, contextRunner);
    }

    @Bean
    @ConfigurationProperties(prefix = "settings.database.circuit-breaker")
    @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerProperties databaseCircuitBreakerProperties() {
        return new CircuitBreakerProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerSecuredJdbcClient circuitBreakerSecuredJdbcClient(
            Vertx vertx,
            JDBCClient vertxJdbcClient,
            Metrics metrics,
            Clock clock,
            ContextRunner contextRunner,
            @Qualifier("databaseCircuitBreakerProperties") CircuitBreakerProperties circuitBreakerProperties) {

        final JdbcClient jdbcClient = createBasicJdbcClient(vertx, vertxJdbcClient, metrics, clock, contextRunner);
        return new CircuitBreakerSecuredJdbcClient(vertx, jdbcClient, metrics,
                circuitBreakerProperties.getOpeningThreshold(), circuitBreakerProperties.getOpeningIntervalMs(),
                circuitBreakerProperties.getClosingIntervalMs(), clock);
    }

    private static BasicJdbcClient createBasicJdbcClient(
            Vertx vertx, JDBCClient vertxJdbcClient, Metrics metrics, Clock clock, ContextRunner contextRunner) {
        final BasicJdbcClient basicJdbcClient = new BasicJdbcClient(vertx, vertxJdbcClient, metrics, clock);

        contextRunner.<Void>runBlocking(promise -> basicJdbcClient.initialize().onComplete(promise));

        return basicJdbcClient;
    }

    @Bean
    @ConfigurationProperties(prefix = "settings.database")
    @Validated
    public DatabaseConfigurationProperties databaseConfigurationProperties() {
        return new DatabaseConfigurationProperties();
    }
}

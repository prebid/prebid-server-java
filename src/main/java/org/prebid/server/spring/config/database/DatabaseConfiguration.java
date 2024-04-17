package org.prebid.server.spring.config.database;

import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.helper.ParametrizedQueryHelper;
import org.prebid.server.settings.helper.ParametrizedQueryMySqlHelper;
import org.prebid.server.settings.helper.ParametrizedQueryPostgresHelper;
import org.prebid.server.spring.config.database.model.ConnectionPoolSettings;
import org.prebid.server.spring.config.database.model.DatabaseAddress;
import org.prebid.server.spring.config.database.properties.DatabaseConfigurationProperties;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.jdbc.BasicJdbcClient;
import org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnExpression("'${settings.database.type}' == 'postgres' or '${settings.database.type}' == 'mysql'")
public class DatabaseConfiguration {

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
    @ConfigurationProperties(prefix = "settings.database")
    @Validated
    public DatabaseConfigurationProperties databaseConfigurationProperties() {
        return new DatabaseConfigurationProperties();
    }

    @Bean
    @ConditionalOnProperty(name = "settings.database.type", havingValue = "mysql")
    ParametrizedQueryHelper mysqlParametrizedQueryHelper() {
        return new ParametrizedQueryMySqlHelper();
    }

    @Bean
    @ConditionalOnProperty(name = "settings.database.type", havingValue = "postgres")
    ParametrizedQueryHelper postgresParametrizedQueryHelper() {
        return new ParametrizedQueryPostgresHelper();
    }

    @Bean
    @ConditionalOnProperty(name = "settings.database.type", havingValue = "mysql")
    Pool mysqlConnectionPool(Vertx vertx,
                             DatabaseAddress databaseAddress,
                             ConnectionPoolSettings connectionPoolSettings) {

        final MySQLConnectOptions sqlConnectOptions = new MySQLConnectOptions()
                .setHost(databaseAddress.getHost())
                .setPort(databaseAddress.getPort())
                .setDatabase(databaseAddress.getDatabaseName())
                .setUser(connectionPoolSettings.getUser())
                .setPassword(connectionPoolSettings.getPassword())
                .setSsl(false)
                .setTcpKeepAlive(true)
                .setIdleTimeout(1)
                .setIdleTimeoutUnit(TimeUnit.SECONDS);

        final PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(connectionPoolSettings.getPoolSize());

        return MySQLBuilder
                .pool()
                .with(poolOptions)
                .connectingTo(sqlConnectOptions)
                .using(vertx)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "settings.database.type", havingValue = "postgres")
    Pool postgresConnectionPool(Vertx vertx,
                                DatabaseAddress databaseAddress,
                                ConnectionPoolSettings connectionPoolSettings) {

        final PgConnectOptions sqlConnectOptions = new PgConnectOptions()
                .setHost(databaseAddress.getHost())
                .setPort(databaseAddress.getPort())
                .setDatabase(databaseAddress.getDatabaseName())
                .setUser(connectionPoolSettings.getUser())
                .setPassword(connectionPoolSettings.getPassword())
                .setSsl(false)
                .setTcpKeepAlive(true)
                .setIdleTimeout(1)
                .setIdleTimeoutUnit(TimeUnit.SECONDS);

        final PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(connectionPoolSettings.getPoolSize());

        return PgBuilder
                .pool()
                .with(poolOptions)
                .connectingTo(sqlConnectOptions)
                .using(vertx)
                .build();
    }

    @Bean
    @ConfigurationProperties(prefix = "settings.database.circuit-breaker")
    @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerProperties databaseCircuitBreakerProperties() {
        return new CircuitBreakerProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    BasicJdbcClient basicJdbcClient(Vertx vertx,
                                    Pool pool,
                                    Metrics metrics,
                                    Clock clock,
                                    ContextRunner contextRunner) {

        return createBasicJdbcClient(vertx, pool, metrics, clock, contextRunner);
    }

    @Bean
    @ConditionalOnProperty(prefix = "settings.database.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerSecuredJdbcClient circuitBreakerSecuredAsyncDatabaseClient(
            Vertx vertx,
            Pool pool,
            Metrics metrics,
            Clock clock,
            ContextRunner contextRunner,
            @Qualifier("databaseCircuitBreakerProperties") CircuitBreakerProperties circuitBreakerProperties) {

        final BasicJdbcClient jdbcClient = createBasicJdbcClient(vertx, pool, metrics, clock, contextRunner);
        return new CircuitBreakerSecuredJdbcClient(
                vertx,
                jdbcClient,
                metrics,
                circuitBreakerProperties.getOpeningThreshold(),
                circuitBreakerProperties.getOpeningIntervalMs(),
                circuitBreakerProperties.getClosingIntervalMs(),
                clock);
    }

    private static BasicJdbcClient createBasicJdbcClient(Vertx vertx,
                                                         Pool pool,
                                                         Metrics metrics,
                                                         Clock clock,
                                                         ContextRunner contextRunner) {

        final BasicJdbcClient basicJdbcClient = new BasicJdbcClient(vertx, pool, metrics, clock);

        contextRunner.<Void>runBlocking(promise -> basicJdbcClient.initialize().onComplete(promise));

        return basicJdbcClient;
    }
}

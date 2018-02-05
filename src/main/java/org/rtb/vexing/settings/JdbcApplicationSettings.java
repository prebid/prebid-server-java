package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.settings.model.Account;
import org.rtb.vexing.spring.config.StoredRequestProperties;

import java.util.Objects;
import java.util.function.Function;

public class JdbcApplicationSettings implements ApplicationSettings {

    private final JDBCClient jdbcClient;

    private JdbcApplicationSettings(JDBCClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public static JdbcApplicationSettings create(Vertx vertx, String url, String driverClass, int maxPoolSize) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(url);
        Objects.requireNonNull(driverClass);
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("maxPoolSize must be positive");
        }

        final JDBCClient jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", url)
                .put("driver_class", driverClass)
                .put("max_pool_size", maxPoolSize));

        return new JdbcApplicationSettings(jdbcClient);
    }

    @Override
    public Future<Account> getAccountById(String accountId) {
        Objects.requireNonNull(accountId);
        return executeQuery("SELECT uuid, price_granularity FROM accounts_account where uuid = ? LIMIT 1", accountId,
                result -> Account.builder()
                        .id(result.getString(0))
                        .priceGranularity(result.getString(1))
                        .build());
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId) {
        Objects.requireNonNull(adUnitConfigId);
        return executeQuery("SELECT config FROM s2sconfig_config where uuid = ? LIMIT 1", adUnitConfigId,
                result -> result.getString(0));
    }

    /**
     * Start acquiring a connection
     */
    @Override
    public Future<Void> initialize() {
        final Future<ResultSet> result = Future.future();
        jdbcClient.query("SELECT 1", result.completer());
        return result.map(ignored -> null);
    }

    private <T> Future<T> executeQuery(String query, String key, Function<JsonArray, T> mapper) {
        Objects.requireNonNull(key);

        final Future<SQLConnection> connectionFuture = Future.future();
        jdbcClient.getConnection(connectionFuture.completer());

        return connectionFuture
                .compose(connection -> {
                    final Future<ResultSet> resultSetFuture = Future.future();
                    connection.queryWithParams(query, new JsonArray().add(key),
                            ar -> {
                                connection.close();
                                resultSetFuture.handle(ar);
                            });
                    return resultSetFuture;
                })
                .map(rs -> {
                    if (rs == null || rs.getResults() == null || rs.getResults().isEmpty()) {
                        throw new PreBidException("Not found");
                    } else {
                        return mapper.apply(rs.getResults().get(0));
                    }
                });
    }

    public static String jdbcUrl(StoredRequestProperties properties, String protocol) {
        return String.format("%s//%s/%s?user=%s&password=%s&useSSL=false",
                protocol,
                Objects.requireNonNull(properties.getHost(), message("host")),
                Objects.requireNonNull(properties.getDbname(), message("dbname")),
                Objects.requireNonNull(properties.getUser(), message("user")),
                Objects.requireNonNull(properties.getPassword(), message("password")));
    }

    private static String message(String field) {
        return String.format("Configuration property datacache.%s is missing", field);
    }
}
